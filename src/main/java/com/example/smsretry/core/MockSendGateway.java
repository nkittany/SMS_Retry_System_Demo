// core/MockSendGateway.java
package com.example.smsretry.core;

import com.example.smsretry.model.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockSendGateway implements SendGateway {

    private final double successRate;

    public MockSendGateway(@Value("${send.successRate:0.7}") double successRate) {
        this.successRate = Math.max(0.0, Math.min(1.0, successRate));
    }

    @Override
    public boolean send(Message message) {
        return ThreadLocalRandom.current().nextDouble() < successRate;
    }
}