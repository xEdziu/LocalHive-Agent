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
        boolean workerApiReady
) {

    public AgentStateSnapshot {
        Objects.requireNonNull(masterConnectionState, "masterConnectionState is required");
        Objects.requireNonNull(workerMode, "workerMode is required");
        Objects.requireNonNull(heartbeatState, "heartbeatState is required");
        lastMessage = normalize(lastMessage);
        lastError = normalize(lastError);
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
                workerApiReady
        );
    }

    public AgentStateSnapshot withConfiguration(AgentConfig config, boolean nextWorkerApiReady) {
        Objects.requireNonNull(config, "config is required");

        return new AgentStateSnapshot(
                resolveMasterConnectionState(config),
                WorkerMode.fromPauseEnabled(config.pauseEnabled()),
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                config.hasWorkerId(),
                nextWorkerApiReady
        );
    }

    public AgentStateSnapshot withMasterConnectionState(MasterConnectionState nextState) {
        return new AgentStateSnapshot(
                nextState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady
        );
    }

    public AgentStateSnapshot withWorkerMode(WorkerMode nextWorkerMode) {
        return new AgentStateSnapshot(
                masterConnectionState,
                nextWorkerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady
        );
    }

    public AgentStateSnapshot withHeartbeatState(HeartbeatState nextHeartbeatState) {
        return new AgentStateSnapshot(
                masterConnectionState,
                workerMode,
                nextHeartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                lastError,
                workerRegistered,
                workerApiReady
        );
    }

    public AgentStateSnapshot withLastMessage(String nextLastMessage) {
        return new AgentStateSnapshot(
                masterConnectionState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                nextLastMessage,
                lastError,
                workerRegistered,
                workerApiReady
        );
    }

    public AgentStateSnapshot withLastError(String nextLastError) {
        return new AgentStateSnapshot(
                masterConnectionState,
                workerMode,
                heartbeatState,
                lastSuccessfulHeartbeat,
                lastMessage,
                nextLastError,
                workerRegistered,
                workerApiReady
        );
    }

    public AgentStateSnapshot withSuccessfulHeartbeat(Instant timestamp, String message) {
        return new AgentStateSnapshot(
                MasterConnectionState.CONNECTED,
                workerMode,
                HeartbeatState.RUNNING,
                Objects.requireNonNull(timestamp, "timestamp is required"),
                message,
                "",
                workerRegistered,
                workerApiReady
        );
    }

    public AgentStateSnapshot withHeartbeatFailure(String error) {
        return new AgentStateSnapshot(
                MasterConnectionState.ATTENTION_REQUIRED,
                workerMode,
                HeartbeatState.FAILED,
                lastSuccessfulHeartbeat,
                lastMessage,
                error,
                workerRegistered,
                workerApiReady
        );
    }

    public AgentStateSnapshot withClearedError() {
        return withLastError("");
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
}
