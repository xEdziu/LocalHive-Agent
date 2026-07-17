package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record CurrentExecution(
        UUID executionId,
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
        leaseToken = requireNonBlank(leaseToken, "leaseToken");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt is required");
        Objects.requireNonNull(status, "status is required");
        lastError = lastError == null ? "" : lastError.trim();
    }

    public static CurrentExecution claimed(ClaimedExecutionPayload payload) {
        Objects.requireNonNull(payload, "payload is required");
        return new CurrentExecution(
                payload.executionId(),
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
                executorId,
                executorContractVersion,
                leaseToken,
                leaseExpiresAt,
                CurrentExecutionStatus.ERROR,
                error
        );
    }

    public String summary() {
        String base = executorId + " / " + status;
        return lastError.isBlank() ? base : base + " / " + lastError;
    }

    @Override
    public String toString() {
        return "CurrentExecution[executionId=" + executionId
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
}
