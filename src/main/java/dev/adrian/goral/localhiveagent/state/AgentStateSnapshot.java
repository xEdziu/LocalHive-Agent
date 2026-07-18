package dev.adrian.goral.localhiveagent.state;

import dev.adrian.goral.localhiveagent.config.AgentConfig;

import java.time.Instant;
import java.util.Objects;

public record AgentStateSnapshot(
        MasterConnectionState masterConnectionState,
        WorkerMode workerMode,
        HeartbeatState heartbeatState,
        Instant lastSuccessfulHeartbeat,
        String lastMessage,
        String lastError,
        boolean workerRegistered,
        boolean workerApiReady,
        boolean taskPollingEnabled,
        String currentExecutionSummary,
        long taskHistoryCount,
        String latestTaskHistorySummary
) {

    public AgentStateSnapshot {
        Objects.requireNonNull(masterConnectionState, "masterConnectionState is required");
        Objects.requireNonNull(workerMode, "workerMode is required");
        Objects.requireNonNull(heartbeatState, "heartbeatState is required");
        if (taskHistoryCount < 0) {
            throw new IllegalArgumentException("taskHistoryCount cannot be negative.");
        }

        lastMessage = normalize(lastMessage);
        lastError = normalize(lastError);
        currentExecutionSummary = normalizeCurrentExecutionSummary(currentExecutionSummary);
        latestTaskHistorySummary = normalizeCurrentExecutionSummary(latestTaskHistorySummary);
    }

    public static AgentStateSnapshot initial(AgentConfig config, boolean workerApiReady, boolean heartbeatRunning) {
        Objects.requireNonNull(config, "config is required");

        return new AgentStateSnapshot(
                config.hasMasterBaseUrl() ? MasterConnectionState.UNKNOWN : MasterConnectionState.NOT_CONFIGURED,
                WorkerMode.fromPauseEnabled(config.pauseEnabled()),
                heartbeatRunning ? HeartbeatState.RUNNING : HeartbeatState.STOPPED,
                null,
                "Ready.",
                "",
                config.hasWorkerId(),
                workerApiReady,
                false,
                "none",
                0,
                "none"
        );
    }

    public AgentStateSnapshot withConfiguration(AgentConfig config, boolean nextWorkerApiReady) {
        Objects.requireNonNull(config, "config is required");

        return copy(
                resolveMasterConnectionState(config),
                WorkerMode.fromPauseEnabled(config.pauseEnabled()),
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                config.hasWorkerId(),
                nextWorkerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withMasterConnectionState(MasterConnectionState nextState) {
        return copy(
                nextState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withWorkerMode(WorkerMode nextWorkerMode) {
        return copy(
                masterConnectionState,
                nextWorkerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withHeartbeatState(HeartbeatState nextHeartbeatState) {
        return copy(
                masterConnectionState,
                workerMode,
                nextHeartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withLastMessage(String nextLastMessage) {
        return copy(
                masterConnectionState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                nextLastMessage,
                lastError,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withLastError(String nextLastError) {
        return copy(
                masterConnectionState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                nextLastError,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withSuccessfulHeartbeat(Instant timestamp, String message) {
        return copy(
                MasterConnectionState.CONNECTED,
                workerMode,
                HeartbeatState.RUNNING,
                Objects.requireNonNull(timestamp, "timestamp is required"),
                message,
                "",
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withHeartbeatFailure(String error) {
        return copy(
                MasterConnectionState.ATTENTION_REQUIRED,
                workerMode,
                HeartbeatState.FAILED,
                lastSuccessfulHeartbeat,
                lastMessage,
                error,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withClearedError() {
        return withLastError("");
    }

    public AgentStateSnapshot withTaskPollingEnabled(boolean nextTaskPollingEnabled) {
        return copy(
                masterConnectionState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady,
                nextTaskPollingEnabled,
                currentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withCurrentExecutionSummary(String nextCurrentExecutionSummary) {
        return copy(
                masterConnectionState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                nextCurrentExecutionSummary,
                taskHistoryCount,
                latestTaskHistorySummary
        );
    }

    public AgentStateSnapshot withTaskHistory(long nextTaskHistoryCount, String nextLatestTaskHistorySummary) {
        return copy(
                masterConnectionState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady,
                taskPollingEnabled,
                currentExecutionSummary,
                nextTaskHistoryCount,
                nextLatestTaskHistorySummary
        );
    }

    private AgentStateSnapshot copy(MasterConnectionState nextMasterConnectionState,
                                    WorkerMode nextWorkerMode,
                                    HeartbeatState nextHeartbeatState,
                                    Instant nextLastSuccessfulHeartbeat,
                                    String nextLastMessage,
                                    String nextLastError,
                                    boolean nextWorkerRegistered,
                                    boolean nextWorkerApiReady,
                                    boolean nextTaskPollingEnabled,
                                    String nextCurrentExecutionSummary,
                                    long nextTaskHistoryCount,
                                    String nextLatestTaskHistorySummary) {
        return new AgentStateSnapshot(
                nextMasterConnectionState,
                nextWorkerMode,
                nextHeartbeatState,
                nextLastSuccessfulHeartbeat,
                nextLastMessage,
                nextLastError,
                nextWorkerRegistered,
                nextWorkerApiReady,
                nextTaskPollingEnabled,
                nextCurrentExecutionSummary,
                nextTaskHistoryCount,
                nextLatestTaskHistorySummary
        );
    }

    private MasterConnectionState resolveMasterConnectionState(AgentConfig config) {
        if (!config.hasMasterBaseUrl()) {
            return MasterConnectionState.NOT_CONFIGURED;
        }

        if (masterConnectionState == MasterConnectionState.NOT_CONFIGURED) {
            return MasterConnectionState.UNKNOWN;
        }

        return masterConnectionState;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeCurrentExecutionSummary(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? "none" : normalized;
    }
}
