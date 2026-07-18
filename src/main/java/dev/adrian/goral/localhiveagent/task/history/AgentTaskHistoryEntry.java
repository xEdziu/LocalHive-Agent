package dev.adrian.goral.localhiveagent.task.history;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AgentTaskHistoryEntry(
        long id,
        UUID executionId,
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
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAtLocal, "createdAtLocal is required");
        Objects.requireNonNull(updatedAtLocal, "updatedAtLocal is required");
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        lastError = normalize(lastError);
    }

    public String summary() {
        String base = executorId + " / " + status;
        return durationMs == null ? base : base + " / " + durationMs + " ms";
    }

    @Override
    public String toString() {
        return "AgentTaskHistoryEntry["
                + "id=" + id
                + ", executionId=" + executionId
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
