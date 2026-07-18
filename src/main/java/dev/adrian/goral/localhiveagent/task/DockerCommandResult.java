package dev.adrian.goral.localhiveagent.task;

import java.util.Objects;

public record DockerCommandResult(
        int exitCode,
        boolean timedOut,
        String stdout,
        String stderr,
        long durationMs,
        String startFailureMessage
) {

    public static final int MAX_STDOUT_CHARS = 16000;
    public static final int MAX_STDERR_CHARS = 16000;

    public DockerCommandResult {
        stdout = bound(stdout, MAX_STDOUT_CHARS);
        stderr = bound(stderr, MAX_STDERR_CHARS);
        startFailureMessage = startFailureMessage == null ? "" : startFailureMessage.trim();
        durationMs = Math.max(0, durationMs);
    }

    public static DockerCommandResult completed(int exitCode, String stdout, String stderr, long durationMs) {
        return new DockerCommandResult(exitCode, false, stdout, stderr, durationMs, "");
    }

    public static DockerCommandResult timedOut(String stdout, String stderr, long durationMs) {
        return new DockerCommandResult(-1, true, stdout, stderr, durationMs, "");
    }

    public static DockerCommandResult failedToStart(String message, long durationMs) {
        return new DockerCommandResult(-1, false, "", "", durationMs, Objects.toString(message, ""));
    }

    public boolean failedToStart() {
        return !startFailureMessage.isBlank();
    }

    @Override
    public String toString() {
        return "DockerCommandResult["
                + "exitCode=" + exitCode
                + ", timedOut=" + timedOut
                + ", stdoutLength=" + stdout.length()
                + ", stderrLength=" + stderr.length()
                + ", durationMs=" + durationMs
                + ", failedToStart=" + failedToStart()
                + ']';
    }

    private static String bound(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }
}
