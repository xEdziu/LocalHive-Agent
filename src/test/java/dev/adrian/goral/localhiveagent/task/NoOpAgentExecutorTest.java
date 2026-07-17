package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpAgentExecutorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldSucceedWithoutPerformingWork() {
        NoOpAgentExecutor executor = new NoOpAgentExecutor();

        AgentExecutionResult result = executor.execute(
                payload(Map.of("message", "hello")),
                new AgentExecutionContext(CLOCK)
        );

        assertTrue(result.success());
        assertEquals("", result.failureCode());
        assertEquals("", result.failureMessage());
    }

    @Test
    void shouldSucceedWithEmptyConfiguration() {
        NoOpAgentExecutor executor = new NoOpAgentExecutor();

        AgentExecutionResult result = executor.execute(
                payload(Map.of()),
                new AgentExecutionContext(CLOCK)
        );

        assertTrue(result.success());
        assertEquals("", result.failureCode());
        assertEquals("", result.failureMessage());
    }

    @Test
    void shouldFailWhenConfigurationCannotBeRead() {
        NoOpAgentExecutor executor = new NoOpAgentExecutor();

        AgentExecutionResult result = executor.execute(
                payload(new ThrowingMap()),
                new AgentExecutionContext(CLOCK)
        );

        assertFalse(result.success());
        assertEquals(NoOpAgentExecutor.FAILURE_CODE, result.failureCode());
        assertTrue(result.failureMessage().contains("configuration unavailable"));
    }

    private static ClaimedExecutionPayload payload(Map<String, Object> configuration) {
        return new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                configuration,
                0,
                0,
                false,
                "lease-token",
                "2026-07-17T12:10:00"
        );
    }

    private static final class ThrowingMap extends HashMap<String, Object> {

        @Override
        public Object get(Object key) {
            throw new IllegalStateException("configuration unavailable");
        }
    }
}
