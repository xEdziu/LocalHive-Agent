package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryEntry;

import java.time.Instant;
import java.util.Optional;

record TaskHistorySummaryViewModel(
        boolean present,
        long totalCount,
        String title,
        String status,
        String executorLabel,
        String executorTechnicalInfo,
        String duration,
        String timestamp,
        String issue
) {

    static TaskHistorySummaryViewModel from(Optional<AgentTaskHistoryEntry> entry, long totalCount) {
        if (entry.isEmpty()) {
            return empty(totalCount);
        }

        AgentTaskHistoryEntry historyEntry = entry.get();
        return new TaskHistorySummaryViewModel(
                true,
                Math.max(0, totalCount),
                ExecutionDisplayFormatter.executionTitle(
                        historyEntry.displayName(),
                        historyEntry.executorId()
                ),
                historyEntry.status().name(),
                ExecutionDisplayFormatter.executorLabel(historyEntry.executorId()),
                ExecutionDisplayFormatter.executorTechnicalInfo(
                        historyEntry.executorId(),
                        historyEntry.executorContractVersion()
                ),
                ExecutionDisplayFormatter.duration(historyEntry.durationMs()),
                ExecutionDisplayFormatter.timestamp(displayTimestamp(historyEntry)),
                issue(historyEntry)
        );
    }

    static TaskHistorySummaryViewModel empty(long totalCount) {
        return new TaskHistorySummaryViewModel(
                false,
                Math.max(0, totalCount),
                "No task history yet",
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE
        );
    }

    String totalCountLabel() {
        return totalCount + (totalCount == 1 ? " record" : " records");
    }

    private static Instant displayTimestamp(AgentTaskHistoryEntry entry) {
        if (entry.completedAt() != null) {
            return entry.completedAt();
        }
        if (entry.startedAt() != null) {
            return entry.startedAt();
        }
        if (entry.claimedAt() != null) {
            return entry.claimedAt();
        }
        return entry.updatedAtLocal();
    }

    private static String issue(AgentTaskHistoryEntry entry) {
        if (!entry.lastError().isBlank()) {
            return entry.lastError();
        }
        if (!entry.failureCode().isBlank() && !entry.failureMessage().isBlank()) {
            return entry.failureCode() + ": " + entry.failureMessage();
        }
        if (!entry.failureCode().isBlank()) {
            return entry.failureCode();
        }
        if (!entry.failureMessage().isBlank()) {
            return entry.failureMessage();
        }

        return ExecutionDisplayFormatter.NO_VALUE;
    }
}
