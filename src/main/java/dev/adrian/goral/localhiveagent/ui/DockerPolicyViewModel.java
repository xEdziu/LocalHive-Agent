package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;

import java.util.List;

record DockerPolicyViewModel(
        String enabled,
        String allowedImages,
        String maxMemoryMb,
        String maxCpuCores,
        String gpuAllowed
) {

    static DockerPolicyViewModel from(DockerPolicy policy) {
        DockerPolicy effectivePolicy = policy == null ? DockerPolicy.defaultPolicy() : policy;
        return new DockerPolicyViewModel(
                ExecutionDisplayFormatter.yesNo(effectivePolicy.enabled()),
                allowedImages(effectivePolicy.allowedImages()),
                effectivePolicy.maxMemoryMb() + " MB",
                String.valueOf(effectivePolicy.maxCpuCores()),
                ExecutionDisplayFormatter.yesNo(effectivePolicy.allowGpu())
        );
    }

    private static String allowedImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return ExecutionDisplayFormatter.NO_VALUE;
        }

        return String.join(", ", images);
    }
}
