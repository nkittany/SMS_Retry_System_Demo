// core/ScheduledItem.java
package com.example.smsretry.core;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class ScheduledItem implements Delayed {
    public final String messageId;
    public final long dueAtMs;

    public ScheduledItem(String messageId, long dueAtMs) {
        this.messageId = messageId;
        this.dueAtMs = dueAtMs;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long delayMs = dueAtMs - System.currentTimeMillis();
        return unit.convert(delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        long d = this.getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
        return (d == 0) ? 0 : (d < 0 ? -1 : 1);
    }
}