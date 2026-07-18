package dev.adrian.goral.localhiveagent.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DockerCommandBuilder {

    public List<String> build(DockerWorkloadConfig config) {
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
        command.add(workloadConfig.image());
        command.addAll(workloadConfig.command());
        return List.copyOf(command);
    }
}
