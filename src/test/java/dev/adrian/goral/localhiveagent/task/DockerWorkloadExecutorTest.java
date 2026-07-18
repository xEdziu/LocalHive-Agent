package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerWorkloadExecutorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReturnUnavailableWhenDockerCliIsMissing() {
        FakeRunner runner = new FakeRunner(DockerCommandResult.completed(0, "", "", 10));
        DockerWorkloadExecutor executor = executor(false, runner);

        AgentExecutionResult result = executor.execute(payload(validConfig()), new AgentExecutionContext(CLOCK));

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.DOCKER_UNAVAILABLE_FAILURE_CODE, result.failureCode());
        assertEquals(0, runner.calls);
    }

    @Test
    void shouldMapExitCodeZeroToSuccess() {
        FakeRunner runner = new FakeRunner(DockerCommandResult.completed(0, "ok", "", 10));
        DockerWorkloadExecutor executor = executor(true, runner);

        AgentExecutionResult result = executor.execute(payload(validConfig()), new AgentExecutionContext(CLOCK));

        assertTrue(result.success());
        assertEquals("", result.failureCode());
        assertEquals(List.of(
                "docker",
                "run",
                "--rm",
                "--network",
                "none",
                "--memory",
                "128m",
                "--cpus",
                "1",
                "alpine:3.20",
                "sh",
                "-c",
                "echo LocalHive Docker workload"
        ), runner.command);
        assertEquals(Duration.ofSeconds(30), runner.timeout);
    }

    @Test
    void shouldMapNonZeroExitCodeToDockerWorkloadFailed() {
        FakeRunner runner = new FakeRunner(DockerCommandResult.completed(2, "", "short error", 10));
        DockerWorkloadExecutor executor = executor(true, runner);

        AgentExecutionResult result = executor.execute(payload(validConfig()), new AgentExecutionContext(CLOCK));

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.WORKLOAD_FAILED_FAILURE_CODE, result.failureCode());
        assertTrue(result.failureMessage().contains("short error"));
    }

    @Test
    void shouldMapTimeoutToDockerWorkloadTimeout() {
        FakeRunner runner = new FakeRunner(DockerCommandResult.timedOut("", "late", 30_000));
        DockerWorkloadExecutor executor = executor(true, runner);

        AgentExecutionResult result = executor.execute(payload(validConfig()), new AgentExecutionContext(CLOCK));

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.TIMEOUT_FAILURE_CODE, result.failureCode());
    }

    @Test
    void shouldMapInvalidConfigurationToFailureCode() {
        DockerWorkloadExecutor executor = executor(true, new FakeRunner(DockerCommandResult.completed(0, "", "", 1)));

        AgentExecutionResult result = executor.execute(
                payload(validConfig("command", List.of())),
                new AgentExecutionContext(CLOCK)
        );

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.INVALID_CONFIGURATION_FAILURE_CODE, result.failureCode());
    }

    @Test
    void shouldMapNonAllowlistedImageToFailureCode() {
        DockerWorkloadExecutor executor = executor(true, new FakeRunner(DockerCommandResult.completed(0, "", "", 1)));

        AgentExecutionResult result = executor.execute(
                payload(validConfig("image", "ubuntu:24.04")),
                new AgentExecutionContext(CLOCK)
        );

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.IMAGE_NOT_ALLOWED_FAILURE_CODE, result.failureCode());
    }

    @Test
    void shouldMapGpuRequiredToInvalidConfiguration() {
        DockerWorkloadExecutor executor = executor(true, new FakeRunner(DockerCommandResult.completed(0, "", "", 1)));

        AgentExecutionResult result = executor.execute(
                payload(validConfig("gpu", Map.of("required", true))),
                new AgentExecutionContext(CLOCK)
        );

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.INVALID_CONFIGURATION_FAILURE_CODE, result.failureCode());
    }

    @Test
    void shouldMapRunnerExceptionToDockerWorkloadFailed() {
        DockerWorkloadExecutor executor = executor(true, new ThrowingRunner());

        AgentExecutionResult result = executor.execute(payload(validConfig()), new AgentExecutionContext(CLOCK));

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.WORKLOAD_FAILED_FAILURE_CODE, result.failureCode());
    }

    private static DockerWorkloadExecutor executor(boolean dockerAvailable, DockerCommandRunner runner) {
        return new DockerWorkloadExecutor(
                new DockerWorkloadConfigParser(),
                new DockerCommandBuilder(),
                () -> dockerAvailable,
                runner
        );
    }

    private static ClaimedExecutionPayload payload(Map<String, Object> configuration) {
        return new ClaimedExecutionPayload(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION,
                configuration,
                128,
                1,
                false,
                "lease-token",
                "2026-07-17T12:10:00"
        );
    }

    private static Map<String, Object> validConfig() {
        return Map.of(
                "image", "alpine:3.20",
                "command", List.of("sh", "-c", "echo LocalHive Docker workload"),
                "timeoutSeconds", 30,
                "resources", Map.of("memoryMb", 128, "cpuCores", 1),
                "gpu", Map.of("required", false)
        );
    }

    private static Map<String, Object> validConfig(String key, Object value) {
        Map<String, Object> config = new HashMap<>(validConfig());
        config.put(key, value);
        return config;
    }

    private static final class FakeRunner implements DockerCommandRunner {

        private final DockerCommandResult result;
        private List<String> command;
        private Duration timeout;
        private int calls;

        private FakeRunner(DockerCommandResult result) {
            this.result = result;
        }

        @Override
        public DockerCommandResult run(List<String> command, Duration timeout) {
            this.command = List.copyOf(command);
            this.timeout = timeout;
            calls++;
            return result;
        }
    }

    private static final class ThrowingRunner implements DockerCommandRunner {

        @Override
        public DockerCommandResult run(List<String> command, Duration timeout) {
            throw new IllegalStateException("runner unavailable");
        }
    }
}
