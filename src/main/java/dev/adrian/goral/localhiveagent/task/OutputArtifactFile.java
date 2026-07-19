package dev.adrian.goral.localhiveagent.task;

import java.nio.file.Path;
import java.util.Objects;

public record OutputArtifactFile(
        Path file,
        String relativePath,
        long sizeBytes
) {

    public OutputArtifactFile {
        Objects.requireNonNull(file, "file is required");
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath cannot be blank.");
        }
        relativePath = relativePath.trim();
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative.");
        }
    }
}
