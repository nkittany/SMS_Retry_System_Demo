// SmsRetryApplication.java
package com.example.smsretry;

import com.example.smsretry.core.SmsRetryEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class SmsRetryApplication {
    private final SmsRetryEngine engine;

    public SmsRetryApplication(SmsRetryEngine engine) {
        this.engine = engine;
    }

    public static void main(String[] args) {
        SpringApplication.run(SmsRetryApplication.class, args);
    }

    // EXACT cadence requirement (every 500ms)
    @Scheduled(fixedRate = 500)
    public void tick() {
        engine.wakeup();
    }
}