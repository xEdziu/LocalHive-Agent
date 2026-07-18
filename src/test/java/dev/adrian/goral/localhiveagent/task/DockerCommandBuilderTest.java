package dev.adrian.goral.localhiveagent.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerCommandBuilderTest {

    @Test
    void shouldBuildSafeDockerRunCommandAsArgumentList() {
        DockerCommandBuilder builder = new DockerCommandBuilder();
        DockerWorkloadConfig config = new DockerWorkloadConfig(
                "alpine:3.20",
                List.of("sh", "-c", "echo LocalHive Docker workload"),
                30,
                128,
                1,
                false,
                null
        );

        List<String> command = builder.build(config);

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
        ), command);
        assertFalse(command.contains("--privileged"));
        assertFalse(command.contains("--network=host"));
        assertFalse(command.contains("--volume"));
        assertFalse(command.contains("-v"));
        assertFalse(command.contains("--env"));
        assertFalse(command.contains("-e"));
        assertFalse(command.contains("--cap-add"));
        assertFalse(command.contains("--pid"));
        assertFalse(command.contains("--ipc"));
    }

    @Test
    void shouldAddReadOnlyWorkspaceMountWhenWorkspaceIsConfigured() {
        DockerCommandBuilder builder = new DockerCommandBuilder();
        DockerWorkloadConfig config = new DockerWorkloadConfig(
                "alpine:3.20",
                List.of("sh", "-c", "ls -la /workspace"),
                30,
                128,
                1,
                false,
                new DockerWorkspaceConfig(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "/workspace",
                        true
                )
        );
        Path workspaceDirectory = Path.of("build", "localhive-workspace");

        List<String> command = builder.build(config, workspaceDirectory);

        assertEquals("--mount", command.get(9));
        assertEquals(
                "type=bind,source="
                        + workspaceDirectory.toAbsolutePath().normalize()
                        + ",target=/workspace,readonly",
                command.get(10)
        );
        assertEquals("alpine:3.20", command.get(11));
        assertEquals("sh", command.get(12));
        assertFalse(command.contains("--privileged"));
        assertFalse(command.contains("--network=host"));
        assertFalse(command.contains("--volume"));
        assertFalse(command.contains("-v"));
        assertFalse(command.toString().contains("docker.sock"));
    }

    @Test
    void shouldRequireAgentGeneratedWorkspaceDirectoryWhenWorkspaceIsConfigured() {
        DockerCommandBuilder builder = new DockerCommandBuilder();
        DockerWorkloadConfig config = new DockerWorkloadConfig(
                "alpine:3.20",
                List.of("sh"),
                30,
                128,
                1,
                false,
                new DockerWorkspaceConfig(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "/workspace",
                        true
                )
        );

        assertThrows(NullPointerException.class, () -> builder.build(config, null));
    }
}
