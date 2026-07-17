package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

public final class CurrentExecutionStore {

    private final AtomicReference<CurrentExecution> currentExecution = new AtomicReference<>();

    public Optional<CurrentExecution> currentExecution() {
        return Optional.ofNullable(currentExecution.get());
    }

    public boolean hasCurrentExecution() {
        return currentExecution.get() != null;
    }

    public CurrentExecution setClaimed(ClaimedExecutionPayload payload) {
        CurrentExecution claimed = CurrentExecution.claimed(payload);
        currentExecution.set(claimed);
        return claimed;
    }

    public CurrentExecution markRunning() {
        return updateRequired(execution -> execution.withStatus(CurrentExecutionStatus.RUNNING));
    }

    public CurrentExecution markSucceeded() {
        return updateRequired(execution -> execution.withStatus(CurrentExecutionStatus.SUCCEEDED));
    }

    public CurrentExecution markFailed() {
        return updateRequired(execution -> execution.withStatus(CurrentExecutionStatus.FAILED));
    }

    public CurrentExecution markError(String error) {
        return updateRequired(execution -> execution.withError(error));
    }

    public CurrentExecution updateLease(LocalDateTime leaseExpiresAt) {
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt is required");
        return updateRequired(execution -> execution.withLeaseExpiresAt(leaseExpiresAt));
    }

    public void clear() {
        currentExecution.set(null);
    }

    public String summary() {
        return currentExecution()
                .map(CurrentExecution::summary)
                .orElse("none");
    }

    private CurrentExecution updateRequired(UnaryOperator<CurrentExecution> updater) {
        Objects.requireNonNull(updater, "updater is required");

        CurrentExecution previous;
        CurrentExecution updated;

        do {
            previous = currentExecution.get();
            if (previous == null) {
                throw new IllegalStateException("Current execution is required.");
            }
            updated = Objects.requireNonNull(updater.apply(previous), "updated current execution is required");
        } while (!currentExecution.compareAndSet(previous, updated));

        return updated;
    }
}
