package dev.adrian.goral.localhiveagent.state;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

public final class AgentStateStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentStateStore.class);

    private final AtomicReference<AgentStateSnapshot> snapshot;
    private final CopyOnWriteArrayList<AgentStateListener> listeners;

    public AgentStateStore(AgentStateSnapshot initialSnapshot) {
        this.snapshot = new AtomicReference<>(Objects.requireNonNull(initialSnapshot));
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public static AgentStateStore fromConfig(
            AgentConfig config,
            boolean workerApiReady,
            boolean heartbeatRunning
    ) {
        return new AgentStateStore(AgentStateSnapshot.initial(config, workerApiReady, heartbeatRunning));
    }

    public AgentStateSnapshot snapshot() {
        return snapshot.get();
    }

    public void addListener(AgentStateListener listener) {
        listeners.addIfAbsent(Objects.requireNonNull(listener));
    }

    public void removeListener(AgentStateListener listener) {
        listeners.remove(listener);
    }

    public void syncConfiguration(AgentConfig config, boolean workerApiReady) {
        update(current -> current.withConfiguration(config, workerApiReady));
    }

    public void setMasterConnectionState(MasterConnectionState state) {
        update(current -> current.withMasterConnectionState(state));
    }

    public void markMasterConnected(String message) {
        update(current -> current
                .withMasterConnectionState(MasterConnectionState.CONNECTED)
                .withLastMessage(message)
                .withClearedError()
        );
    }

    public void markMasterAttentionRequired(String error) {
        update(current -> current
                .withMasterConnectionState(MasterConnectionState.ATTENTION_REQUIRED)
                .withLastError(error)
        );
    }

    public void setWorkerMode(WorkerMode workerMode) {
        update(current -> current.withWorkerMode(workerMode));
    }

    public void setHeartbeatState(HeartbeatState heartbeatState) {
        update(current -> current.withHeartbeatState(heartbeatState));
    }

    public void setLastMessage(String message) {
        update(current -> current.withLastMessage(message).withClearedError());
    }

    public void setLastError(String error) {
        update(current -> current.withLastError(error));
    }

    public void recordSuccessfulHeartbeat(Instant timestamp, String message) {
        update(current -> current.withSuccessfulHeartbeat(timestamp, message));
    }

    public void recordHeartbeatFailure(String error) {
        update(current -> current.withHeartbeatFailure(error));
    }

    public void update(UnaryOperator<AgentStateSnapshot> updater) {
        Objects.requireNonNull(updater);

        AgentStateSnapshot previous;
        AgentStateSnapshot updated;

        do {
            previous = snapshot.get();
            updated = Objects.requireNonNull(updater.apply(previous), "updated snapshot is required");

            if (previous.equals(updated)) {
                return;
            }
        } while (!snapshot.compareAndSet(previous, updated));

        publish(updated);
    }

    @Override
    public void close() {
        listeners.clear();
    }

    private void publish(AgentStateSnapshot updatedSnapshot) {
        for (AgentStateListener listener : listeners) {
            try {
                listener.onStateChanged(updatedSnapshot);
            } catch (RuntimeException exception) {
                log.warn("Agent state listener failed: {}", exception.getMessage());
            }
        }
    }
}
