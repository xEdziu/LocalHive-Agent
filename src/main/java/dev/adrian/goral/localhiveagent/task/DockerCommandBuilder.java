package dev.adrian.goral.localhiveagent.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DockerCommandBuilder {

    public List<String> build(DockerWorkloadConfig config) {
        return build(config, null);
    }

    public List<String> build(DockerWorkloadConfig config, Path workspaceDirectory) {
        return build(config, workspaceDirectory, null);
    }

    public List<String> build(DockerWorkloadConfig config, Path workspaceDirectory, Path outputDirectory) {
        DockerWorkloadConfig workloadConfig = Objects.requireNonNull(config, "config is required");

        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("--network");
        command.add("none");
        command.add("--memory");
        command.add(workloadConfig.memoryMb() + "m");
        command.add("--cpus");
        command.add(Integer.toString(workloadConfig.cpuCores()));
        if (workloadConfig.workspace() != null) {
            command.add("--mount");
            command.add(workspaceMountArgument(workloadConfig, workspaceDirectory));
        }
        if (outputDirectory != null) {
            command.add("--mount");
            command.add(outputMountArgument(outputDirectory));
        }
        command.add(workloadConfig.image());
        command.addAll(workloadConfig.command());
        return List.copyOf(command);
    }

    private static String workspaceMountArgument(DockerWorkloadConfig config, Path workspaceDirectory) {
        Path source = Objects.requireNonNull(workspaceDirectory, "workspaceDirectory is required")
                .toAbsolutePath()
                .normalize();
        return "type=bind,source="
                + source
                + ",target="
                + config.workspace().mountPath()
                + ",readonly";
    }

    private static String outputMountArgument(Path outputDirectory) {
        Path source = Objects.requireNonNull(outputDirectory, "outputDirectory is required")
                .toAbsolutePath()
                .normalize();
        return "type=bind,source="
                + source
                + ",target=/output";
    }
}
