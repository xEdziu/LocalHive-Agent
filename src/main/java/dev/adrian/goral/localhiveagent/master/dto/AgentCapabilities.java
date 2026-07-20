package dev.adrian.goral.localhiveagent.master.dto;

import java.util.List;

public record AgentCapabilities(
        List<ExecutorCapability> executors,
        DockerCapability docker
) {
    public AgentCapabilities {
        executors = executors == null ? List.of() : List.copyOf(executors);
    }

    public record ExecutorCapability(
            String executorId,
            int executorContractVersion,
            boolean enabled
    ) {
    }

    public record DockerCapability(
            boolean enabled,
            List<String> allowedImages,
            int maxMemoryMb,
            int maxCpuCores,
            boolean gpuAllowed
    ) {
        public DockerCapability {
            allowedImages = allowedImages == null ? List.of() : List.copyOf(allowedImages);
        }
    }
}
