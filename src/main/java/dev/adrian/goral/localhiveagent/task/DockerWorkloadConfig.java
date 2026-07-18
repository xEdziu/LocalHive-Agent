package dev.adrian.goral.localhiveagent.task;

import java.util.List;
import java.util.Objects;

public record DockerWorkloadConfig(
        String image,
        List<String> command,
        int timeoutSeconds,
        int memoryMb,
        int cpuCores,
        boolean gpuRequired,
        DockerWorkspaceConfig workspace
) {

    public DockerWorkloadConfig {
        image = requireNonBlank(image, "image");
        command = List.copyOf(Objects.requireNonNull(command, "command is required"));
        timeoutSeconds = requirePositive(timeoutSeconds, "timeoutSeconds");
        memoryMb = requirePositive(memoryMb, "memoryMb");
        cpuCores = requirePositive(cpuCores, "cpuCores");
    }

    @Override
    public String toString() {
        return "DockerWorkloadConfig["
                + "image=" + image
                + ", commandSize=" + command.size()
                + ", timeoutSeconds=" + timeoutSeconds
                + ", memoryMb=" + memoryMb
                + ", cpuCores=" + cpuCores
                + ", gpuRequired=" + gpuRequired
                + ", workspace=" + (workspace != null)
                + ']';
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    private static int requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive.");
        }
        return value;
    }
}
