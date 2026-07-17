package dev.adrian.goral.localhiveagent.master.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimedExecutionPayloadTest {

    @Test
    void shouldRedactLeaseTokenFromToString() {
        ClaimedExecutionPayload payload = new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                "localhive.no-op",
                1,
                Map.of("message", "noop"),
                0,
                0,
                false,
                "raw-lease-token",
                "2026-07-17T12:10:00"
        );

        String text = payload.toString();

        assertFalse(text.contains("raw-lease-token"));
        assertTrue(text.contains("leaseToken=<redacted>"));
        assertTrue(text.contains("executionId=223e4567-e89b-12d3-a456-426614174000"));
        assertTrue(text.contains("executorId=localhive.no-op"));
    }
}
