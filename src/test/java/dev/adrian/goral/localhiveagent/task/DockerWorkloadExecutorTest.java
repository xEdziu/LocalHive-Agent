package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.master.MasterClientException;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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
    private static final UUID WORKER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID WORKSPACE_ARTIFACT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

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
    void shouldPrepareWorkspaceAndMountItReadOnlyBeforeRunningDocker() {
        FakeRunner runner = new FakeRunner(DockerCommandResult.completed(0, "ok", "", 10));
        Path workspaceDirectory = Path.of("build", "workspace-test");
        FakeWorkspacePreparer workspacePreparer = new FakeWorkspacePreparer(new PreparedWorkspace(workspaceDirectory));
        DockerWorkloadExecutor executor = executor(DockerPolicy.defaultPolicy(), true, runner, workspacePreparer);
        AgentExecutionContext context = context();

        AgentExecutionResult result = executor.execute(payload(validConfig("workspace", validWorkspaceConfig())), context);

        assertTrue(result.success());
        assertEquals(1, workspacePreparer.calls);
        assertEquals(context, workspacePreparer.context);
        assertEquals(WORKSPACE_ARTIFACT_ID, workspacePreparer.workspace.artifactId());
        assertEquals("--mount", runner.command.get(9));
        assertEquals(
                "type=bind,source=" + workspaceDirectory.toAbsolutePath().normalize() + ",target=/workspace,readonly",
                runner.command.get(10)
        );
        assertEquals("alpine:3.20", runner.command.get(11));
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
    void shouldMapInvalidWorkspaceConfigurationToInvalidConfiguration() {
        Map<String, Object> workspace = new HashMap<>(validWorkspaceConfig());
        workspace.put("mountPath", "/data");
        DockerWorkloadExecutor executor = executor(true, new FakeRunner(DockerCommandResult.completed(0, "", "", 1)));

        AgentExecutionResult result = executor.execute(
                payload(validConfig("workspace", workspace)),
                context()
        );

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.INVALID_CONFIGURATION_FAILURE_CODE, result.failureCode());
    }

    @Test
    void shouldMapMissingWorkspaceMountPathToInvalidConfiguration() {
        Map<String, Object> workspace = new HashMap<>(validWorkspaceConfig());
        workspace.remove("mountPath");
        DockerWorkloadExecutor executor = executor(true, new FakeRunner(DockerCommandResult.completed(0, "", "", 1)));

        AgentExecutionResult result = executor.execute(
                payload(validConfig("workspace", workspace)),
                context()
        );

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.INVALID_CONFIGURATION_FAILURE_CODE, result.failureCode());
    }

    @Test
    void shouldMapMissingWorkspaceReadOnlyToInvalidConfiguration() {
        Map<String, Object> workspace = new HashMap<>(validWorkspaceConfig());
        workspace.remove("readOnly");
        DockerWorkloadExecutor executor = executor(true, new FakeRunner(DockerCommandResult.completed(0, "", "", 1)));

        AgentExecutionResult result = executor.execute(
                payload(validConfig("workspace", workspace)),
                context()
        );

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.INVALID_CONFIGURATION_FAILURE_CODE, result.failureCode());
    }

    @Test
    void shouldReturnDisabledWhenDockerPolicyDisablesWorkloads() {
        FakeRunner runner = new FakeRunner(DockerCommandResult.completed(0, "", "", 1));
        DockerPolicy policy = new DockerPolicy(false, List.of("alpine:3.20"), 4096, 8, false);
        DockerWorkloadExecutor executor = executor(policy, true, runner);

        AgentExecutionResult result = executor.execute(payload(validConfig()), new AgentExecutionContext(CLOCK));

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.DOCKER_DISABLED_FAILURE_CODE, result.failureCode());
        assertEquals(0, runner.calls);
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

    @Test
    void shouldMapWorkspaceDownloadFailure() {
        DockerWorkloadExecutor executor = executor(
                DockerPolicy.defaultPolicy(),
                true,
                new FakeRunner(DockerCommandResult.completed(0, "", "", 1)),
                new ThrowingWorkspacePreparer(new MasterClientException("download failed"))
        );

        AgentExecutionResult result = executor.execute(payload(validConfig("workspace", validWorkspaceConfig())), context());

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.WORKSPACE_ARTIFACT_DOWNLOAD_FAILED_CODE, result.failureCode());
    }

    @Test
    void shouldMapUnsafeWorkspacePackage() {
        DockerWorkloadExecutor executor = executor(
                DockerPolicy.defaultPolicy(),
                true,
                new FakeRunner(DockerCommandResult.completed(0, "", "", 1)),
                new ThrowingWorkspacePreparer(new WorkspacePackageInvalidException("zip slip"))
        );

        AgentExecutionResult result = executor.execute(payload(validConfig("workspace", validWorkspaceConfig())), context());

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.WORKSPACE_PACKAGE_INVALID_CODE, result.failureCode());
    }

    @Test
    void shouldMapUnexpectedWorkspaceUnpackFailure() {
        DockerWorkloadExecutor executor = executor(
                DockerPolicy.defaultPolicy(),
                true,
                new FakeRunner(DockerCommandResult.completed(0, "", "", 1)),
                new ThrowingWorkspacePreparer(new WorkspaceUnpackException("disk unavailable", new RuntimeException()))
        );

        AgentExecutionResult result = executor.execute(payload(validConfig("workspace", validWorkspaceConfig())), context());

        assertFalse(result.success());
        assertEquals(DockerWorkloadExecutor.WORKSPACE_UNPACK_FAILED_CODE, result.failureCode());
    }

    private static DockerWorkloadExecutor executor(boolean dockerAvailable, DockerCommandRunner runner) {
        return executor(DockerPolicy.defaultPolicy(), dockerAvailable, runner);
    }

    private static DockerWorkloadExecutor executor(DockerPolicy policy, boolean dockerAvailable, DockerCommandRunner runner) {
        return new DockerWorkloadExecutor(
                () -> policy,
                new DockerWorkloadConfigParser(),
                new DockerCommandBuilder(),
                () -> dockerAvailable,
                runner
        );
    }

    private static DockerWorkloadExecutor executor(DockerPolicy policy,
                                                   boolean dockerAvailable,
                                                   DockerCommandRunner runner,
                                                   WorkspacePreparer workspacePreparer) {
        return new DockerWorkloadExecutor(
                () -> policy,
                new DockerWorkloadConfigParser(),
                new DockerCommandBuilder(),
                () -> dockerAvailable,
                runner,
                workspacePreparer
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

    private static Map<String, Object> validWorkspaceConfig() {
        return Map.of(
                "artifactId", WORKSPACE_ARTIFACT_ID.toString(),
                "mountPath", "/workspace",
                "readOnly", true
        );
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(CLOCK, "http://localhost:8080", WORKER_ID, "worker-api-key");
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

    private static final class FakeWorkspacePreparer implements WorkspacePreparer {

        private final PreparedWorkspace result;
        private AgentExecutionContext context;
        private DockerWorkspaceConfig workspace;
        private int calls;

        private FakeWorkspacePreparer(PreparedWorkspace result) {
            this.result = result;
        }

        @Override
        public PreparedWorkspace prepare(AgentExecutionContext context,
                                         ClaimedExecutionPayload payload,
                                         DockerWorkspaceConfig workspace) {
            this.context = context;
            this.workspace = workspace;
            calls++;
            return result;
        }
    }

    private static final class ThrowingWorkspacePreparer implements WorkspacePreparer {

        private final RuntimeException exception;

        private ThrowingWorkspacePreparer(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public PreparedWorkspace prepare(AgentExecutionContext context,
                                         ClaimedExecutionPayload payload,
                                         DockerWorkspaceConfig workspace) {
            throw exception;
        }
    }
}
