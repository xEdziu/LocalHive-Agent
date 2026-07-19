package dev.adrian.goral.localhiveagent.master.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimedExecutionPayloadTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void shouldRedactLeaseTokenFromToString() {
        ClaimedExecutionPayload payload = new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                "NO-OP smoke test",
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
        assertTrue(text.contains("displayName=NO-OP smoke test"));
        assertTrue(text.contains("executorId=localhive.no-op"));
    }

    @Test
    void shouldTrimDisplayName() {
        ClaimedExecutionPayload payload = new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                "  Custom execution  ",
                "localhive.no-op",
                1,
                Map.of(),
                0,
                0,
                false,
                "raw-lease-token",
                "2026-07-17T12:10:00"
        );

        assertEquals("Custom execution", payload.displayName());
        assertEquals("Custom execution", payload.displayNameOrFallback());
    }

    @Test
    void shouldFallbackWhenDisplayNameIsBlank() {
        ClaimedExecutionPayload payload = new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                "   ",
                "localhive.no-op",
                1,
                Map.of(),
                0,
                0,
                false,
                "raw-lease-token",
                "2026-07-17T12:10:00"
        );

        assertNull(payload.displayName());
        assertEquals("NO-OP smoke test", payload.displayNameOrFallback());
    }

    @Test
    void shouldFallbackToDockerImageWhenDisplayNameIsMissing() {
        ClaimedExecutionPayload payload = new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                null,
                "localhive.docker.workload",
                1,
                Map.of("image", "alpine:3.20"),
                128,
                1,
                false,
                "raw-lease-token",
                "2026-07-17T12:10:00"
        );

        assertEquals("Docker workload: alpine:3.20", payload.displayNameOrFallback());
    }

    @Test
    void shouldFallbackToNoOpNameWhenDisplayNameIsMissing() {
        ClaimedExecutionPayload payload = new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                null,
                "localhive.no-op",
                1,
                Map.of("message", "noop"),
                0,
                0,
                false,
                "raw-lease-token",
                "2026-07-17T12:10:00"
        );

        assertEquals("NO-OP smoke test", payload.displayNameOrFallback());
    }

    @Test
    void shouldFallbackToExecutorIdForGenericExecutor() {
        ClaimedExecutionPayload payload = new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                null,
                "adrian.custom-task",
                1,
                Map.of(),
                0,
                0,
                false,
                "raw-lease-token",
                "2026-07-17T12:10:00"
        );

        assertEquals("adrian.custom-task", payload.displayNameOrFallback());
    }

    @Test
    void shouldRejectTooLongDisplayName() {
        String tooLong = "x".repeat(256);

        assertThrows(IllegalArgumentException.class, () -> new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                tooLong,
                "localhive.no-op",
                1,
                Map.of(),
                0,
                0,
                false,
                "raw-lease-token",
                "2026-07-17T12:10:00"
        ));
    }

    @Test
    void shouldDeserializeFutureAdditiveFields() {
        ClaimedExecutionPayload payload = JSON_MAPPER.readValue("""
                {
                  "executionId": "223e4567-e89b-12d3-a456-426614174000",
                  "displayName": "Future safe task",
                  "executorId": "localhive.no-op",
                  "executorContractVersion": 1,
                  "configuration": {"message": "hello"},
                  "requiredRamMb": 0,
                  "requiredCpuCores": 0,
                  "gpuRequired": false,
                  "leaseToken": "lease-token",
                  "leaseExpiresAt": "2026-07-17T12:10:00",
                  "futureField": "ignored"
                }
                """, ClaimedExecutionPayload.class);

        assertEquals("Future safe task", payload.displayName());
    }
}
