package dev.adrian.goral.localhiveagent.task;

import java.nio.file.Path;
import java.util.Objects;

public record PreparedOutputDirectory(Path directory) {

    public PreparedOutputDirectory {
        Objects.requireNonNull(directory, "directory is required");
    }
}
