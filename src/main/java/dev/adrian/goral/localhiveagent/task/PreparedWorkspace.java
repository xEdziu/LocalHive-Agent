package dev.adrian.goral.localhiveagent.task;

import java.nio.file.Path;
import java.util.Objects;

public record PreparedWorkspace(Path directory) {

    public PreparedWorkspace {
        Objects.requireNonNull(directory, "directory is required");
    }
}
