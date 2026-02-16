// store/S3Writer.java
package com.example.smsretry.store;

import com.example.smsretry.model.MessageState;
import com.example.smsretry.util.Json;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class S3Writer {

    private final S3Client s3;
    private final String bucket;

    private final BlockingQueue<StateUpdate> queue = new LinkedBlockingQueue<>(200_000);

    // coalesce latest update per messageId to reduce write load
    private final ConcurrentHashMap<String, StateUpdate> latest = new ConcurrentHashMap<>();

    private final ExecutorService writers = Executors.newFixedThreadPool(2);

    public S3Writer(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;

        for (int i = 0; i < 2; i++) {
            writers.submit(this::runWriter);
        }
    }

    public void enqueue(StateUpdate u) {
        // keep only latest for each messageId
        latest.put(u.state.messageId, u);
        queue.offer(u); // best-effort
    }

    private void runWriter() {
        List<StateUpdate> batch = new ArrayList<>(2000);
        while (true) {
            try {
                StateUpdate first = queue.take();
                batch.clear();
                batch.add(first);

                // drain quickly for batching
                queue.drainTo(batch, 2000);

                // For each messageId, write only the latest update
                Map<String, StateUpdate> toWrite = new HashMap<>();
                for (StateUpdate u : batch) {
                    StateUpdate cur = latest.get(u.state.messageId);
                    if (cur != null)
                        toWrite.put(u.state.messageId, cur);
                }

                for (StateUpdate u : toWrite.values()) {
                    // remove only if still same instance to avoid races
                    latest.remove(u.state.messageId, u);
                    putState(u);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // swallow & continue (in real life: log)
            }
        }
    }

    private void putState(StateUpdate u) throws Exception {
        MessageState s = u.state;
        String key = keyFor(u.kind, s);
        byte[] bytes = Json.MAPPER.writeValueAsBytes(s);

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .build();

        s3.putObject(req, RequestBody.fromBytes(bytes));
    }

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH").withZone(ZoneOffset.UTC);

    private String keyFor(StateUpdate.Kind kind, MessageState s) {
        if (kind == StateUpdate.Kind.PENDING) {
            int shard = (s.messageId.hashCode() & 0x7fffffff) % 256;
            return "state/pending/" + shard + "/" + s.messageId + ".json";
        }
        String ts = DT.format(Instant.now());
        String folder = (kind == StateUpdate.Kind.SUCCESS) ? "success" : "failed";
        return "state/" + folder + "/" + ts + "/" + s.messageId + ".json";
    }
}