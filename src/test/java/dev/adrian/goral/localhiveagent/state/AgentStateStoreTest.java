package dev.adrian.goral.localhiveagent.state;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStateStoreTest {

    @Test
    void returnsInitialState() {
        AgentStateStore store = AgentStateStore.fromConfig(AgentConfig.empty(), false, false);

        AgentStateSnapshot snapshot = store.snapshot();

        assertEquals(MasterConnectionState.NOT_CONFIGURED, snapshot.masterConnectionState());
        assertEquals(WorkerMode.ACTIVE, snapshot.workerMode());
        assertEquals(HeartbeatState.STOPPED, snapshot.heartbeatState());
        assertNull(snapshot.lastSuccessfulHeartbeat());
        assertEquals("Ready.", snapshot.lastMessage());
        assertEquals("", snapshot.lastError());
        assertFalse(snapshot.workerRegistered());
        assertFalse(snapshot.workerApiReady());
    }

    @Test
    void updatesWorkerMode() {
        AgentStateStore store = AgentStateStore.fromConfig(AgentConfig.empty(), false, false);

        store.setWorkerMode(WorkerMode.PAUSED);

        assertEquals(WorkerMode.PAUSED, store.snapshot().workerMode());
    }

    @Test
    void updatesHeartbeatState() {
        AgentStateStore store = AgentStateStore.fromConfig(AgentConfig.empty(), false, false);

        store.setHeartbeatState(HeartbeatState.STARTING);

        assertEquals(HeartbeatState.STARTING, store.snapshot().heartbeatState());
    }

    @Test
    void recordsSuccessfulHeartbeatTimestamp() {
        AgentStateStore store = AgentStateStore.fromConfig(readyConfig(), true, true);
        Instant timestamp = Instant.parse("2026-07-11T12:00:00Z");

        store.recordSuccessfulHeartbeat(timestamp, "Heartbeat completed.");

        AgentStateSnapshot snapshot = store.snapshot();
        assertEquals(HeartbeatState.RUNNING, snapshot.heartbeatState());
        assertEquals(MasterConnectionState.CONNECTED, snapshot.masterConnectionState());
        assertEquals(timestamp, snapshot.lastSuccessfulHeartbeat());
        assertEquals("Heartbeat completed.", snapshot.lastMessage());
        assertEquals("", snapshot.lastError());
    }

    @Test
    void heartbeatFailureKeepsLastSuccessfulTimestamp() {
        AgentStateStore store = AgentStateStore.fromConfig(readyConfig(), true, true);
        Instant timestamp = Instant.parse("2026-07-11T12:00:00Z");

        store.recordSuccessfulHeartbeat(timestamp, "Heartbeat completed.");
        store.recordHeartbeatFailure("Heartbeat failed.");

        AgentStateSnapshot snapshot = store.snapshot();
        assertEquals(HeartbeatState.FAILED, snapshot.heartbeatState());
        assertEquals(MasterConnectionState.ATTENTION_REQUIRED, snapshot.masterConnectionState());
        assertEquals(timestamp, snapshot.lastSuccessfulHeartbeat());
        assertEquals("Heartbeat failed.", snapshot.lastError());
    }

    @Test
    void listenerReceivesNewSnapshot() {
        AgentStateStore store = AgentStateStore.fromConfig(AgentConfig.empty(), false, false);
        AtomicReference<AgentStateSnapshot> receivedSnapshot = new AtomicReference<>();

        store.addListener(receivedSnapshot::set);
        store.setLastMessage("Config saved.");

        assertEquals("Config saved.", receivedSnapshot.get().lastMessage());
    }

    @Test
    void removedListenerDoesNotReceiveFurtherChanges() {
        AgentStateStore store = AgentStateStore.fromConfig(AgentConfig.empty(), false, false);
        AtomicInteger callCount = new AtomicInteger();
        AgentStateListener listener = snapshot -> callCount.incrementAndGet();

        store.addListener(listener);
        store.setLastMessage("First");
        store.removeListener(listener);
        store.setLastMessage("Second");

        assertEquals(1, callCount.get());
    }

    @Test
    void failingListenerDoesNotBlockOtherListeners() {
        AgentStateStore store = AgentStateStore.fromConfig(AgentConfig.empty(), false, false);
        AtomicReference<AgentStateSnapshot> receivedSnapshot = new AtomicReference<>();

        store.addListener(snapshot -> {
            throw new IllegalStateException("listener failed");
        });
        store.addListener(receivedSnapshot::set);

        store.setLastMessage("Config saved.");

        assertEquals("Config saved.", receivedSnapshot.get().lastMessage());
    }

    @Test
    void keepsConsistentStateDuringConcurrentUpdates() throws InterruptedException {
        AgentStateStore store = AgentStateStore.fromConfig(readyConfig(), true, true);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        int updateCount = 200;

        for (int index = 0; index < updateCount; index++) {
            int value = index;

            executor.submit(() -> {
                try {
                    assertTrue(startLatch.await(5, TimeUnit.SECONDS));
                    store.update(snapshot -> snapshot.withLastMessage("update-" + value));
                    store.setWorkerMode(value % 2 == 0 ? WorkerMode.ACTIVE : WorkerMode.PAUSED);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();

        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertSame(store.snapshot(), store.snapshot());
        assertTrue(store.snapshot().lastMessage().startsWith("update-"));
        assertTrue(store.snapshot().workerMode() == WorkerMode.ACTIVE
                || store.snapshot().workerMode() == WorkerMode.PAUSED);
    }

    private static AgentConfig readyConfig() {
        return new AgentConfig(
                "http://localhost:8080",
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                8192,
                false
        );
    }
}
