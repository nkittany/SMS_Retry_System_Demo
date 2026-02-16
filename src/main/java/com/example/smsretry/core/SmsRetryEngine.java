// core/SmsRetryEngine.java
package com.example.smsretry.core;

import com.example.smsretry.model.Message;
import com.example.smsretry.model.MessageState;
import com.example.smsretry.model.MessageStatus;
import com.example.smsretry.store.StateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SmsRetryEngine {

    private final SchedulerShard[] shards;
    private final int shardCount;

    private final SendGateway sendGateway;
    private final StateStore store;

    public SmsRetryEngine(
            SendGateway sendGateway,
            StateStore store,
            @Value("${scheduler.shards:16}") int shardCount,
            @Value("${scheduler.retryThreadsPerShard:2}") int threadsPerShard) {
        this.sendGateway = sendGateway;
        this.store = store;
        this.shardCount = Math.max(1, shardCount);
        this.shards = new SchedulerShard[this.shardCount];
        for (int i = 0; i < this.shardCount; i++) {
            shards[i] = new SchedulerShard(Math.max(1, threadsPerShard), sendGateway, store);
        }
    }

    @PostConstruct
    public void recoverFromS3() {
        List<MessageState> pending = store.loadPendingAll();
        long now = System.currentTimeMillis();
        for (MessageState s : pending) {
            if (s.status != MessageStatus.PENDING)
                continue;
            shardOf(s.messageId).upsertState(s);
            long due = s.nextDueAtMs;
            if (due <= 0)
                due = now; // safety
            shardOf(s.messageId).schedule(s.messageId, due);
        }
    }

    private SchedulerShard shardOf(String messageId) {
        int h = messageId.hashCode();
        int idx = (h & 0x7fffffff) % shardCount;
        return shards[idx];
    }

    // spec: called on every message arrival
    public String newMessage(Message incomingWithoutId) {
        String messageId = incomingWithoutId.messageId();
        if (messageId == null || messageId.isBlank()) {
            messageId = "msg-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
        }

        Message msg = new Message(messageId, incomingWithoutId.phone(), incomingWithoutId.body());
        SchedulerShard shard = shardOf(messageId);

        // idempotency-ish: if already exists and not terminal, just return same id
        MessageState existing = shard.getState(messageId);
        if (existing != null && existing.status == MessageStatus.PENDING) {
            return messageId;
        }

        long now = System.currentTimeMillis();
        MessageState state = MessageState.fromMessage(msg, now);
        shard.upsertState(state);

        // Attempt #1 immediately inside newMessage()
        boolean ok;
        try {
            ok = sendGateway.send(msg);
        } catch (Exception e) {
            ok = false;
            state.lastError = e.getMessage();
        }

        state.attemptCount = 1;

        if (ok) {
            state.status = MessageStatus.SUCCESS;
            state.nextDueAtMs = 0L;
            store.enqueue(com.example.smsretry.store.StateUpdate.success(state));
            shard.recordTerminal(state);
            return messageId;
        }

        // Failed attempt #1 -> schedule attempt #2 at +500ms from arrival
        state.status = MessageStatus.PENDING;
        long due = state.arrivalAtMs + RetryDelays.delayFromArrivalMs(2);
        state.nextDueAtMs = due;

        store.enqueue(com.example.smsretry.store.StateUpdate.pending(state));
        shard.schedule(messageId, due);
        return messageId;
    }

    // spec: called every 500ms (exact)
    public void wakeup() {
        for (SchedulerShard shard : shards) {
            shard.drainDueAndDispatch();
        }
        // flush is handled by async writers; wakeup stays light
    }

    public List<Map<String, Object>> getRecentSuccess(int limit) {
        int lim = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (SchedulerShard shard : shards) {
            for (MessageState s : shard.snapshotSuccess(lim))
                out.add(toSummary(s));
        }
        out.sort((a, b) -> Long.compare((long) b.get("finalTimestampMs"), (long) a.get("finalTimestampMs")));
        return out.subList(0, Math.min(lim, out.size()));
    }

    public List<Map<String, Object>> getRecentFailed(int limit) {
        int lim = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (SchedulerShard shard : shards) {
            for (MessageState s : shard.snapshotFailed(lim))
                out.add(toSummary(s));
        }
        out.sort((a, b) -> Long.compare((long) b.get("finalTimestampMs"), (long) a.get("finalTimestampMs")));
        return out.subList(0, Math.min(lim, out.size()));
    }

    private Map<String, Object> toSummary(MessageState s) {
        long ts = (s.status == MessageStatus.PENDING) ? s.nextDueAtMs : System.currentTimeMillis();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("messageId", s.messageId);
        m.put("attemptCount", s.attemptCount);
        m.put("status", s.status.name());
        m.put("finalTimestampMs", ts);
        m.put("reason", s.lastError);
        return m;
    }
}