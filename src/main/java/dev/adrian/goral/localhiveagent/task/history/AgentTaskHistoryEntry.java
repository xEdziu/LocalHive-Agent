package dev.adrian.goral.localhiveagent.task.history;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AgentTaskHistoryEntry(
        long id,
        UUID executionId,
        String displayName,
        String executorId,
        int executorContractVersion,
        AgentTaskHistoryStatus status,
        Instant claimedAt,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        String failureCode,
        String failureMessage,
        String lastError,
        Instant createdAtLocal,
        Instant updatedAtLocal
) {

    public AgentTaskHistoryEntry {
        Objects.requireNonNull(executionId, "executionId is required");
        executorId = requireNonBlank(executorId, "executorId");
        displayName = normalizeDisplayName(displayName);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAtLocal, "createdAtLocal is required");
        Objects.requireNonNull(updatedAtLocal, "updatedAtLocal is required");
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        lastError = normalize(lastError);
    }

    public String summary() {
        String base = displayNameOrFallback() + " / " + status;
        return durationMs == null ? base : base + " / " + durationMs + " ms";
    }

    public String displayNameOrFallback() {
        if (displayName != null) {
            return displayName;
        }
        if ("localhive.no-op".equals(executorId)) {
            return "NO-OP smoke test";
        }

        return executorId;
    }

    @Override
    public String toString() {
        return "AgentTaskHistoryEntry["
                + "id=" + id
                + ", executionId=" + executionId
                + ", displayName=" + displayName
                + ", executorId=" + executorId
                + ", executorContractVersion=" + executorContractVersion
                + ", status=" + status
                + ", claimedAt=" + claimedAt
                + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt
                + ", durationMs=" + durationMs
                + ", failureCode=" + failureCode
                + ", failureMessage=" + failureMessage
                + ", lastError=" + lastError
                + ", createdAtLocal=" + createdAtLocal
                + ", updatedAtLocal=" + updatedAtLocal
                + ']';
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }

        return value.trim();
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("displayName must be at most 255 characters.");
        }

        return trimmed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
