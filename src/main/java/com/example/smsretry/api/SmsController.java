// api/SmsController.java
package com.example.smsretry.api;

import com.example.smsretry.core.SmsRetryEngine;
import com.example.smsretry.model.Message;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class SmsController {
    private final SmsRetryEngine engine;

    public SmsController(SmsRetryEngine engine) {
        this.engine = engine;
    }

    public record SendRequest(String phone, String body) {
    }

    @PostMapping("/messages")
    public Map<String, Object> sendOne(@RequestBody SendRequest req) {
        String id = engine.newMessage(new Message(null, req.phone(), req.body()));
        return Map.of("messageId", id);
    }

    @PostMapping("/messages/repeat")
    public Map<String, Object> sendRepeat(@RequestParam("count") int count, @RequestBody SendRequest req) {
        int n = Math.max(1, Math.min(200_000, count));
        List<String> ids = new ArrayList<>(Math.min(n, 10_000));
        for (int i = 0; i < n; i++) {
            ids.add(engine.newMessage(new Message(null, req.phone(), req.body())));
        }
        // If N is huge, returning all IDs can be heavy; you can return a summary
        // instead.
        return Map.of("count", n, "messageIds", ids);
    }

    @GetMapping("/messages/success")
    public List<Map<String, Object>> success(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return engine.getRecentSuccess(limit);
    }

    @GetMapping("/messages/failed")
    public List<Map<String, Object>> failed(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return engine.getRecentFailed(limit);
    }
}