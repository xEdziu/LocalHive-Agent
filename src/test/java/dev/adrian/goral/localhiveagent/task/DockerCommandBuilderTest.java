package dev.adrian.goral.localhiveagent.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
                false
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
}
