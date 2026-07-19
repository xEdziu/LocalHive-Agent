package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.task.AgentExecutorRegistry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

final class ExecutionDisplayFormatter {

    static final String NO_VALUE = "-";
    static final String NO_OP_LABEL = "NO-OP";
    static final String NO_OP_TITLE = "NO-OP smoke test";
    static final String DOCKER_WORKLOAD_LABEL = "Docker workload";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private ExecutionDisplayFormatter() {
    }

    static String executionTitle(String displayName, String executorId) {
        String normalizedDisplayName = normalize(displayName);
        if (!normalizedDisplayName.equals(NO_VALUE)) {
            return normalizedDisplayName;
        }

        if (AgentExecutorRegistry.NO_OP_EXECUTOR_ID.equals(executorId)) {
            return NO_OP_TITLE;
        }

        if (AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID.equals(executorId)) {
            return DOCKER_WORKLOAD_LABEL;
        }

        String normalizedExecutorId = normalize(executorId);
        return normalizedExecutorId.equals(NO_VALUE) ? "Execution" : normalizedExecutorId;
    }

    static String executorLabel(String executorId) {
        if (AgentExecutorRegistry.NO_OP_EXECUTOR_ID.equals(executorId)) {
            return NO_OP_LABEL;
        }

        if (AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID.equals(executorId)) {
            return DOCKER_WORKLOAD_LABEL;
        }

        return normalize(executorId);
    }

    static String executorTechnicalInfo(String executorId, int executorContractVersion) {
        return normalize(executorId) + " / contract v" + executorContractVersion;
    }

    static String duration(Long durationMs) {
        if (durationMs == null) {
            return NO_VALUE;
        }

        long boundedDurationMs = Math.max(0, durationMs);
        if (boundedDurationMs < 1000) {
            return boundedDurationMs + " ms";
        }

        return String.format(Locale.ROOT, "%.1f s", boundedDurationMs / 1000.0);
    }

    static String timestamp(Instant timestamp) {
        return timestamp == null ? NO_VALUE : TIMESTAMP_FORMATTER.format(timestamp);
    }

    static String uuid(UUID value) {
        return value == null ? NO_VALUE : value.toString();
    }

    static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    static String normalize(String value) {
        if (value == null) {
            return NO_VALUE;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? NO_VALUE : trimmed;
    }
}
