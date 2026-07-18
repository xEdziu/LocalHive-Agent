package dev.adrian.goral.localhiveagent.task;

import java.util.Objects;
import java.util.UUID;

public record DockerWorkspaceConfig(
        UUID artifactId,
        String mountPath,
        boolean readOnly
) {

    public DockerWorkspaceConfig {
        Objects.requireNonNull(artifactId, "artifactId is required");
        if (!"/workspace".equals(mountPath)) {
            throw new IllegalArgumentException("mountPath must be /workspace.");
        }
        if (!readOnly) {
            throw new IllegalArgumentException("readOnly must be true.");
        }
    }
}
