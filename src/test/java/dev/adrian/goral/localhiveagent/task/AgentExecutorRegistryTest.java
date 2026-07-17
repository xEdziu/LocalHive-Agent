package dev.adrian.goral.localhiveagent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorRegistryTest {

    @Test
    void shouldContainDefaultNoOpExecutor() {
        AgentExecutorRegistry registry = AgentExecutorRegistry.withDefaultExecutors();

        assertTrue(registry.findExecutor(
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION
        ).isPresent());
    }

    @Test
    void shouldResolveExecutorsByIdentifierAndContractVersion() {
        AgentExecutorRegistry registry = new AgentExecutorRegistry();
        AgentExecutor executor = (payload, context) -> AgentExecutionResult.succeeded();

        registry.register("localhive.test", 2, executor);

        assertSame(executor, registry.findExecutor("localhive.test", 2).orElseThrow());
        assertFalse(registry.findExecutor("localhive.test", 1).isPresent());
        assertFalse(registry.findExecutor("localhive.unknown", 2).isPresent());
    }

    @Test
    void shouldRejectBlankExecutorIdentifier() {
        AgentExecutorRegistry registry = new AgentExecutorRegistry();
        AgentExecutor executor = (payload, context) -> AgentExecutionResult.succeeded();

        assertThrows(IllegalArgumentException.class, () -> registry.register("", 1, executor));
    }
}
