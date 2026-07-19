package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentExecutionStoreTest {

    @Test
    void shouldTrackCurrentExecutionTransitionsWithoutExposingLeaseToken() {
        CurrentExecutionStore store = new CurrentExecutionStore();

        CurrentExecution claimed = store.setClaimed(payload(
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                "2026-07-17T12:10:00"
        ));
        assertEquals(CurrentExecutionStatus.CLAIMED, claimed.status());
        assertEquals("NO-OP smoke test", claimed.displayName());
        assertEquals("NO-OP smoke test / CLAIMED", claimed.summary());
        assertTrue(store.hasCurrentExecution());
        assertFalse(claimed.summary().contains("lease-token"));
        assertFalse(claimed.toString().contains("lease-token"));

        CurrentExecution running = store.markRunning();
        assertEquals(CurrentExecutionStatus.RUNNING, running.status());
        assertEquals("NO-OP smoke test / RUNNING", running.summary());
        assertFalse(running.summary().contains("lease-token"));
        assertFalse(running.toString().contains("lease-token"));

        LocalDateTime renewedLease = LocalDateTime.parse("2026-07-17T12:30:00");
        CurrentExecution renewed = store.updateLease(renewedLease);
        assertEquals(renewedLease, renewed.leaseExpiresAt());

        CurrentExecution error = store.markError("terminal report failed");
        assertEquals(CurrentExecutionStatus.ERROR, error.status());
        assertEquals("terminal report failed", error.lastError());
        assertFalse(error.summary().contains("lease-token"));
        assertFalse(error.toString().contains("lease-token"));

        store.clear();
        assertFalse(store.hasCurrentExecution());
        assertEquals("none", store.summary());
    }

    @Test
    void shouldRejectStateTransitionWithoutCurrentExecution() {
        CurrentExecutionStore store = new CurrentExecutionStore();

        assertThrows(IllegalStateException.class, store::markRunning);
    }

    @Test
    void shouldUseClaimDisplayNameInCurrentExecutionSummary() {
        CurrentExecutionStore store = new CurrentExecutionStore();

        CurrentExecution claimed = store.setClaimed(new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                "Custom execution",
                "localhive.no-op",
                1,
                Map.of("message", "hello"),
                0,
                0,
                false,
                "lease-token",
                "2026-07-17T12:10:00"
        ));

        assertEquals("Custom execution", claimed.displayName());
        assertEquals("Custom execution / CLAIMED", store.summary());
        assertEquals("localhive.no-op", claimed.executorId());
    }

    static ClaimedExecutionPayload payload(String executorId, int executorContractVersion, String leaseExpiresAt) {
        return new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                executorId,
                executorContractVersion,
                Map.of("message", "hello"),
                0,
                0,
                false,
                "lease-token",
                leaseExpiresAt
        );
    }
}
