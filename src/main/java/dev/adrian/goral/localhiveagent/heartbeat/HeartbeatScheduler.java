package dev.adrian.goral.localhiveagent.heartbeat;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.master.RegistrationClient;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatRequest;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatResponse;
import dev.adrian.goral.localhiveagent.security.CredentialStore;
import dev.adrian.goral.localhiveagent.state.AgentStateStore;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class HeartbeatScheduler implements AutoCloseable {

    private final ConfigService configService;
    private final RegistrationClient registrationClient;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running;
    private final CredentialStore credentialStore;
    private final AgentStateStore agentStateStore;

    private volatile ScheduledFuture<?> scheduledTask;

    public HeartbeatScheduler(
            ConfigService configService,
            CredentialStore credentialStore,
            RegistrationClient registrationClient,
            AgentStateStore agentStateStore
    ) {
        this.configService = Objects.requireNonNull(configService);
        this.registrationClient = Objects.requireNonNull(registrationClient);
        this.credentialStore = Objects.requireNonNull(credentialStore);
        this.agentStateStore = Objects.requireNonNull(agentStateStore);
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "localhive-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.running = new AtomicBoolean(false);
    }

    public void start(Duration interval, Consumer<HeartbeatTickResult> resultConsumer) {
        Objects.requireNonNull(interval);
        Objects.requireNonNull(resultConsumer);

        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Heartbeat interval must be positive.");
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        long intervalSeconds = Math.max(1, interval.toSeconds());

        scheduledTask = executor.scheduleWithFixedDelay(
                () -> executeHeartbeat(resultConsumer),
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        running.set(false);

        ScheduledFuture<?> currentTask = scheduledTask;

        if (currentTask != null) {
            currentTask.cancel(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }

    private void executeHeartbeat(Consumer<HeartbeatTickResult> resultConsumer) {
        if (!running.get()) {
            return;
        }

        try {
            AgentConfig config = configService.load();
            validateConfigBeforeHeartbeat(config);

            String apiKey = credentialStore.loadApiKey()
                    .orElseThrow(() -> new IllegalStateException("API key is required."));

            HeartbeatRequest request = new HeartbeatRequest(
                    config.pauseEnabled(),
                    config.sharedRamMb()
            );

            HeartbeatResponse response = registrationClient.sendHeartbeat(
                    config.masterBaseUrl(),
                    config.workerId(),
                    apiKey,
                    request
            );

            HeartbeatTickResult result = HeartbeatTickResult.success(response.status());
            agentStateStore.recordSuccessfulHeartbeat(result.timestamp(), result.message());
            resultConsumer.accept(result);
        } catch (RuntimeException exception) {
            HeartbeatTickResult result = HeartbeatTickResult.failure(exception);
            agentStateStore.recordHeartbeatFailure(result.message());
            resultConsumer.accept(result);
        }
    }

    private static void validateConfigBeforeHeartbeat(AgentConfig config) {
        if (!config.hasMasterBaseUrl()) {
            throw new IllegalStateException("Master base URL is required before heartbeat.");
        }

        if (!config.hasWorkerId()) {
            throw new IllegalStateException("Worker ID is required before heartbeat.");
        }
    }
}
