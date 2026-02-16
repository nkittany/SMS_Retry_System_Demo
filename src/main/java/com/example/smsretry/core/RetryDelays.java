// core/RetryDelays.java
package com.example.smsretry.core;

public final class RetryDelays {
    private RetryDelays() {
    }

    // attempt number -> delay from arrival (ms)
    public static long delayFromArrivalMs(int attemptNumber) {
        return switch (attemptNumber) {
            case 1 -> 0L;
            case 2 -> 500L;
            case 3 -> 2000L;
            case 4 -> 4000L;
            case 5 -> 8000L;
            case 6 -> 16000L;
            default -> throw new IllegalArgumentException("attemptNumber must be 1..6");
        };
    }
}