package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record CurrentExecution(
        UUID executionId,
        String displayName,
        String executorId,
        int executorContractVersion,
        String leaseToken,
        LocalDateTime leaseExpiresAt,
        CurrentExecutionStatus status,
        String lastError
) {

    public CurrentExecution {
        Objects.requireNonNull(executionId, "executionId is required");
        executorId = requireNonBlank(executorId, "executorId");
        displayName = normalizeDisplayName(displayName, executorId);
        leaseToken = requireNonBlank(leaseToken, "leaseToken");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt is required");
        Objects.requireNonNull(status, "status is required");
        lastError = lastError == null ? "" : lastError.trim();
    }

    public static CurrentExecution claimed(ClaimedExecutionPayload payload) {
        Objects.requireNonNull(payload, "payload is required");
        return new CurrentExecution(
                payload.executionId(),
                payload.displayNameOrFallback(),
                payload.executorId(),
                payload.executorContractVersion(),
                payload.leaseToken(),
                payload.leaseExpiresAtDateTime(),
                CurrentExecutionStatus.CLAIMED,
                ""
        );
    }

    public CurrentExecution withStatus(CurrentExecutionStatus nextStatus) {
        return new CurrentExecution(
                executionId,
                displayName,
                executorId,
                executorContractVersion,
                leaseToken,
                leaseExpiresAt,
                nextStatus,
                lastError
        );
    }

    public CurrentExecution withLeaseExpiresAt(LocalDateTime nextLeaseExpiresAt) {
        return new CurrentExecution(
                executionId,
                displayName,
                executorId,
                executorContractVersion,
                leaseToken,
                nextLeaseExpiresAt,
                status,
                lastError
        );
    }

    public CurrentExecution withError(String error) {
        return new CurrentExecution(
                executionId,
                displayName,
                executorId,
                executorContractVersion,
                leaseToken,
                leaseExpiresAt,
                CurrentExecutionStatus.ERROR,
                error
        );
    }

    public String summary() {
        String base = displayName + " / " + status;
        return lastError.isBlank() ? base : base + " / " + lastError;
    }

    @Override
    public String toString() {
        return "CurrentExecution[executionId=" + executionId
                + ", displayName=" + displayName
                + ", executorId=" + executorId
                + ", executorContractVersion=" + executorContractVersion
                + ", leaseExpiresAt=" + leaseExpiresAt
                + ", status=" + status
                + ", lastError=" + lastError
                + "]";
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }

        return value;
    }

    private static String normalizeDisplayName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String trimmed = value.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("displayName must be at most 255 characters.");
        }

        return trimmed;
    }
}
