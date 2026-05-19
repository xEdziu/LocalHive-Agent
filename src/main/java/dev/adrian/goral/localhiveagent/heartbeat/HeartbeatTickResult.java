package dev.adrian.goral.localhiveagent.heartbeat;

import java.time.Instant;

public record HeartbeatTickResult(
        boolean success,
        Instant timestamp,
        String message,
        Throwable error
) {

    public static HeartbeatTickResult success(String status) {
        return new HeartbeatTickResult(
                true,
                Instant.now(),
                "Heartbeat completed. Status: " + status,
                null
        );
    }

    public static HeartbeatTickResult failure(Throwable error) {
        return new HeartbeatTickResult(
                false,
                Instant.now(),
                "Heartbeat failed: " + error.getMessage(),
                error
        );
    }
}