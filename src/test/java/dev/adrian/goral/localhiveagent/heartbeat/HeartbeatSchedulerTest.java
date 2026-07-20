package dev.adrian.goral.localhiveagent.heartbeat;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.master.MasterClientException;
import dev.adrian.goral.localhiveagent.master.RegistrationClient;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatRequest;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatResponse;
import dev.adrian.goral.localhiveagent.security.CredentialStore;
import dev.adrian.goral.localhiveagent.state.AgentStateSnapshot;
import dev.adrian.goral.localhiveagent.state.AgentStateStore;
import dev.adrian.goral.localhiveagent.state.HeartbeatState;
import dev.adrian.goral.localhiveagent.state.MasterConnectionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatSchedulerTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldRecordSuccessfulHeartbeatInStateStore() {
        TestFixture fixture = createFixture();
        AtomicReference<HeartbeatTickResult> result = new AtomicReference<>();

        fixture.scheduler.start(Duration.ofSeconds(1), result::set);
        fixture.scheduler.runHeartbeatOnce(result::set);

        AgentStateSnapshot snapshot = fixture.agentStateStore.snapshot();
        assertTrue(result.get().success());
        assertEquals(HeartbeatState.RUNNING, snapshot.heartbeatState());
        assertEquals(MasterConnectionState.CONNECTED, snapshot.masterConnectionState());
        assertNotNull(snapshot.lastSuccessfulHeartbeat());
        assertEquals("", snapshot.lastError());
        assertEquals(4096, fixture.registrationClient.lastRequest.sharedRamMb());
        assertFalse(fixture.registrationClient.lastRequest.pauseEnabled());
        assertNotNull(fixture.registrationClient.lastRequest.capabilities());
        assertEquals(2, fixture.registrationClient.lastRequest.capabilities().executors().size());
        assertEquals("localhive.no-op", fixture.registrationClient.lastRequest.capabilities()
                .executors().get(0).executorId());
        assertEquals(1, fixture.registrationClient.lastRequest.capabilities()
                .executors().get(0).executorContractVersion());
        assertTrue(fixture.registrationClient.lastRequest.capabilities().executors().get(0).enabled());
        assertEquals("localhive.docker.workload", fixture.registrationClient.lastRequest.capabilities()
                .executors().get(1).executorId());
        assertEquals(1, fixture.registrationClient.lastRequest.capabilities()
                .executors().get(1).executorContractVersion());
        assertTrue(fixture.registrationClient.lastRequest.capabilities().executors().get(1).enabled());
        assertTrue(fixture.registrationClient.lastRequest.capabilities().docker().enabled());
        assertEquals(List.of("alpine:3.20"), fixture.registrationClient.lastRequest.capabilities().docker().allowedImages());
        assertEquals(4096, fixture.registrationClient.lastRequest.capabilities().docker().maxMemoryMb());
        assertEquals(8, fixture.registrationClient.lastRequest.capabilities().docker().maxCpuCores());
        assertFalse(fixture.registrationClient.lastRequest.capabilities().docker().gpuAllowed());
    }

    @Test
    void shouldReportDockerExecutorDisabledWhenDockerPolicyIsDisabled() {
        TestFixture fixture = createFixture(new AgentConfig(
                "http://localhost:8080",
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                4096,
                false,
                new DockerPolicy(false, List.of("alpine:3.20"), 4096, 8, false)
        ));
        AtomicReference<HeartbeatTickResult> result = new AtomicReference<>();

        fixture.scheduler.start(Duration.ofSeconds(1), result::set);
        fixture.scheduler.runHeartbeatOnce(result::set);

        assertTrue(result.get().success());
        assertFalse(fixture.registrationClient.lastRequest.capabilities().executors().get(1).enabled());
        assertFalse(fixture.registrationClient.lastRequest.capabilities().docker().enabled());
    }

    @Test
    void shouldRecordFailedHeartbeatAndKeepPreviousSuccessfulTimestamp() {
        TestFixture fixture = createFixture();

        fixture.scheduler.start(Duration.ofSeconds(1), ignored -> {
        });
        fixture.scheduler.runHeartbeatOnce(ignored -> {
        });
        var previousTimestamp = fixture.agentStateStore.snapshot().lastSuccessfulHeartbeat();

        fixture.registrationClient.failHeartbeat = true;
        fixture.scheduler.runHeartbeatOnce(ignored -> {
        });

        AgentStateSnapshot snapshot = fixture.agentStateStore.snapshot();
        assertEquals(HeartbeatState.FAILED, snapshot.heartbeatState());
        assertEquals(MasterConnectionState.ATTENTION_REQUIRED, snapshot.masterConnectionState());
        assertSame(previousTimestamp, snapshot.lastSuccessfulHeartbeat());
        assertTrue(snapshot.lastError().contains("Heartbeat failed"));
    }

    @Test
    void shouldReflectRunningAndStoppedLifecycle() {
        TestFixture fixture = createFixture();

        fixture.scheduler.start(Duration.ofSeconds(1), ignored -> {
        });

        assertTrue(fixture.scheduler.isRunning());
        assertEquals(HeartbeatState.RUNNING, fixture.agentStateStore.snapshot().heartbeatState());
        assertNotNull(fixture.executor.scheduledCommand);

        fixture.scheduler.stop();

        assertFalse(fixture.scheduler.isRunning());
        assertEquals(HeartbeatState.STOPPED, fixture.agentStateStore.snapshot().heartbeatState());
        assertTrue(fixture.executor.future.cancelled);
    }

    private TestFixture createFixture() {
        return createFixture(new AgentConfig(
                "http://localhost:8080",
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                4096,
                false
        ));
    }

    private TestFixture createFixture(AgentConfig config) {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));
        configService.save(config);

        AgentStateStore agentStateStore = AgentStateStore.fromConfig(configService.load(), true, false);
        FakeRegistrationClient registrationClient = new FakeRegistrationClient();
        RecordingScheduledExecutorService executor = new RecordingScheduledExecutorService();

        HeartbeatScheduler scheduler = new HeartbeatScheduler(
                configService,
                new StaticCredentialStore("test-api-key"),
                registrationClient,
                agentStateStore,
                executor
        );

        return new TestFixture(scheduler, agentStateStore, registrationClient, executor);
    }

    private record TestFixture(
            HeartbeatScheduler scheduler,
            AgentStateStore agentStateStore,
            FakeRegistrationClient registrationClient,
            RecordingScheduledExecutorService executor
    ) {
    }

    private static final class StaticCredentialStore implements CredentialStore {

        private final String apiKey;

        private StaticCredentialStore(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public void saveApiKey(String apiKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> loadApiKey() {
            return Optional.of(apiKey);
        }

        @Override
        public void deleteApiKey() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRegistrationClient extends RegistrationClient {

        private boolean failHeartbeat;
        private HeartbeatRequest lastRequest;

        @Override
        public HeartbeatResponse sendHeartbeat(
                String masterBaseUrl,
                UUID workerId,
                String apiKey,
                HeartbeatRequest request
        ) {
            lastRequest = request;

            if (failHeartbeat) {
                throw new MasterClientException("Master communication: Master did not respond in time.");
            }

            return new HeartbeatResponse("success");
        }
    }

    private static final class RecordingScheduledExecutorService
            extends AbstractExecutorService implements ScheduledExecutorService {

        private final RecordingScheduledFuture future = new RecordingScheduledFuture();
        private Runnable scheduledCommand;
        private boolean shutdown;

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit
        ) {
            this.scheduledCommand = command;
            return future;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command,
                long initialDelay,
                long period,
                TimeUnit unit
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingScheduledFuture implements ScheduledFuture<Object> {

        private boolean cancelled;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
