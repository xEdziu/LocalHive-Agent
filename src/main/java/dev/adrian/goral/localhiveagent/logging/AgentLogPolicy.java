package dev.adrian.goral.localhiveagent.logging;

import dev.adrian.goral.localhiveagent.app.AgentPaths;

import java.nio.file.Path;
import java.util.Objects;

public final class AgentLogPolicy {

    public static final int MAX_LOG_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_LOG_FILE_COUNT = 5;

    private static final String LOG_FILE_NAME_PREFIX = "localhive-agent";
    private static final String LOG_FILE_EXTENSION = ".log";

    private final Path logDirectory;
    private final int maxFileSizeBytes;
    private final int maxFileCount;

    public AgentLogPolicy(Path logDirectory, int maxFileSizeBytes, int maxFileCount) {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("Maximum log file size must be positive.");
        }

        if (maxFileCount <= 0) {
            throw new IllegalArgumentException("Maximum log file count must be positive.");
        }

        this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory is required");
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxFileCount = maxFileCount;
    }

    public static AgentLogPolicy defaultPolicy() {
        return new AgentLogPolicy(
                AgentPaths.logsDirectory(),
                MAX_LOG_FILE_SIZE_BYTES,
                MAX_LOG_FILE_COUNT
        );
    }

    public Path logDirectory() {
        return logDirectory;
    }

    public int maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public int maxFileCount() {
        return maxFileCount;
    }

    public long maximumExpectedDiskUsageBytes() {
        return (long) maxFileSizeBytes * maxFileCount;
    }

    Path logFilePath(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Log file index cannot be negative.");
        }

        return logDirectory.resolve(LOG_FILE_NAME_PREFIX + "." + index + LOG_FILE_EXTENSION);
    }

    boolean isManagedLogFile(String fileName) {
        if (fileName == null
                || !fileName.startsWith(LOG_FILE_NAME_PREFIX + ".")
                || !fileName.endsWith(LOG_FILE_EXTENSION)) {
            return false;
        }

        String indexPart = fileName.substring(
                LOG_FILE_NAME_PREFIX.length() + 1,
                fileName.length() - LOG_FILE_EXTENSION.length()
        );

        try {
            return Integer.parseInt(indexPart) >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    int extractLogFileIndex(String fileName) {
        if (!isManagedLogFile(fileName)) {
            throw new IllegalArgumentException("File is not managed by LocalHive logging: " + fileName);
        }

        String indexPart = fileName.substring(
                LOG_FILE_NAME_PREFIX.length() + 1,
                fileName.length() - LOG_FILE_EXTENSION.length()
        );

        return Integer.parseInt(indexPart);
    }
}
