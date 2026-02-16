// core/SchedulerShard.java
package com.example.smsretry.core;

import com.example.smsretry.model.MessageState;
import com.example.smsretry.model.MessageStatus;
import com.example.smsretry.store.StateStore;
import com.example.smsretry.store.StateUpdate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.*;

public class SchedulerShard {
    private final DelayQueue<ScheduledItem> delayQueue = new DelayQueue<>();
    private final ConcurrentHashMap<String, MessageState> states = new ConcurrentHashMap<>();
    private final ExecutorService retryPool;
    private final SendGateway sendGateway;
    private final StateStore store;
    private final Deque<MessageState> lastSuccess = new ArrayDeque<>();
    private final Deque<MessageState> lastFailed = new ArrayDeque<>();
    private final Object recentLock = new Object();

    public SchedulerShard(int threads, SendGateway sendGateway, StateStore store) {
        this.retryPool = Executors.newFixedThreadPool(threads);
        this.sendGateway = sendGateway;
        this.store = store;
    }

    public void shutdown() {
        retryPool.shutdown();
    }

    public MessageState getState(String messageId) {
        return states.get(messageId);
    }

    public void upsertState(MessageState s) {
        states.put(s.messageId, s);
    }

    public void schedule(String messageId, long dueAtMs) {
        delayQueue.offer(new ScheduledItem(messageId, dueAtMs));
    }

    public void recordTerminal(MessageState s) {
        synchronized (recentLock) {
            Deque<MessageState> target = (s.status == MessageStatus.SUCCESS) ? lastSuccess : lastFailed;
            target.addFirst(s);
            while (target.size() > 100)
                target.removeLast();
        }
    }

    public Deque<MessageState> snapshotSuccess(int limit) {
        synchronized (recentLock) {
            return copyDeque(lastSuccess, limit);
        }
    }

    public Deque<MessageState> snapshotFailed(int limit) {
        synchronized (recentLock) {
            return copyDeque(lastFailed, limit);
        }
    }

    private Deque<MessageState> copyDeque(Deque<MessageState> src, int limit) {
        Deque<MessageState> out = new ArrayDeque<>();
        int i = 0;
        for (MessageState s : src) {
            if (i++ >= limit)
                break;
            out.addLast(s);
        }
        return out;
    }

    // Called by wakeup(): drain due items fast, submit work to pool
    public void drainDueAndDispatch() {
        while (true) {
            ScheduledItem item = delayQueue.poll(); // returns due-only
            if (item == null)
                return;

            retryPool.submit(() -> processRetry(item.messageId));
        }
    }

    private void processRetry(String messageId) {
        MessageState s = states.get(messageId);
        if (s == null)
            return;
        if (s.status != MessageStatus.PENDING)
            return;

        long now = System.currentTimeMillis();
        if (s.nextDueAtMs > now) {
            // Not due yet; reschedule (rare because DelayQueue already guards)
            schedule(messageId, s.nextDueAtMs);
            return;
        }

        // Next attempt number is attemptCount + 1
        int nextAttempt = s.attemptCount + 1;
        if (nextAttempt > 6)
            return;

        boolean ok;
        try {
            ok = sendGateway.send(s.toMessage());
        } catch (Exception e) {
            ok = false;
            s.lastError = e.getMessage();
        }

        s.attemptCount = nextAttempt;

        if (ok) {
            s.status = MessageStatus.SUCCESS;
            s.nextDueAtMs = 0L;
            store.enqueue(StateUpdate.success(s));
            recordTerminal(s);
            return;
        }

        // Failed
        if (nextAttempt >= 6) {
            s.status = MessageStatus.FAILED;
            s.nextDueAtMs = 0L;
            if (s.lastError == null)
                s.lastError = "All retries exhausted";
            store.enqueue(StateUpdate.failed(s));
            recordTerminal(s);
            return;
        }

        // Schedule next due based on arrival + delay table
        int upcomingAttempt = nextAttempt + 1;
        long due = s.arrivalAtMs + RetryDelays.delayFromArrivalMs(upcomingAttempt);
        s.nextDueAtMs = due;

        store.enqueue(StateUpdate.pending(s));
        schedule(messageId, due);
    }
}