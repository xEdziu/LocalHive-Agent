package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorRegistryTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldContainDefaultNoOpExecutor() {
        AgentExecutorRegistry registry = AgentExecutorRegistry.withDefaultExecutors();

        assertTrue(registry.findExecutor(
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION
        ).isPresent());
    }

    @Test
    void shouldContainDefaultDockerWorkloadExecutor() {
        AgentExecutorRegistry registry = AgentExecutorRegistry.withDefaultExecutors();

        assertTrue(registry.findExecutor(
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION
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

    @Test
    void shouldApplyDockerPolicyFromConfigService() {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));
        configService.save(AgentConfig.empty().withDocker(
                new DockerPolicy(false, List.of("alpine:3.20"), 4096, 8, false)
        ));
        AgentExecutorRegistry registry = AgentExecutorRegistry.withDefaultExecutors(configService);
        AgentExecutor executor = registry.findExecutor(
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION
        ).orElseThrow();

        AgentExecutionResult result = executor.execute(
                dockerPayload(),
                new AgentExecutionContext(Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC))
        );

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.DOCKER_DISABLED_FAILURE_CODE, result.failureCode());
    }

    private static ClaimedExecutionPayload dockerPayload() {
        return new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION,
                Map.of(
                        "image", "alpine:3.20",
                        "command", List.of("sh", "-c", "echo LocalHive Docker workload"),
                        "timeoutSeconds", 30,
                        "resources", Map.of("memoryMb", 128, "cpuCores", 1),
                        "gpu", Map.of("required", false)
                ),
                128,
                1,
                false,
                "lease-token",
                "2026-07-17T12:10:00"
        );
    }
}
