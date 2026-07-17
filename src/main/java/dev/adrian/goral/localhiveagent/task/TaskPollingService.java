package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.master.MasterTaskClient;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import dev.adrian.goral.localhiveagent.security.CredentialStore;
import dev.adrian.goral.localhiveagent.state.AgentStateStore;
import dev.adrian.goral.localhiveagent.validation.AgentConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TaskPollingService implements AutoCloseable {

    public static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(10);
    public static final Duration LEASE_RENEWAL_THRESHOLD = Duration.ofSeconds(20);
    public static final String UNSUPPORTED_EXECUTOR_FAILURE_CODE = "UNSUPPORTED_EXECUTOR";

    private static final Logger log = LoggerFactory.getLogger(TaskPollingService.class);

    private final ConfigService configService;
    private final CredentialStore credentialStore;
    private final MasterTaskClient taskClient;
    private final AgentExecutorRegistry executorRegistry;
    private final CurrentExecutionStore currentExecutionStore;
    private final AgentStateStore agentStateStore;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> scheduledTask;

    public TaskPollingService(ConfigService configService,
                              CredentialStore credentialStore,
                              MasterTaskClient taskClient,
                              AgentExecutorRegistry executorRegistry,
                              CurrentExecutionStore currentExecutionStore,
                              AgentStateStore agentStateStore) {
        this(
                configService,
                credentialStore,
                taskClient,
                executorRegistry,
                currentExecutionStore,
                agentStateStore,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "localhive-task-polling");
                    thread.setDaemon(true);
                    return thread;
                }),
                Clock.systemDefaultZone()
        );
    }

    TaskPollingService(ConfigService configService,
                       CredentialStore credentialStore,
                       MasterTaskClient taskClient,
                       AgentExecutorRegistry executorRegistry,
                       CurrentExecutionStore currentExecutionStore,
                       AgentStateStore agentStateStore,
                       ScheduledExecutorService executor,
                       Clock clock) {
        this.configService = Objects.requireNonNull(configService);
        this.credentialStore = Objects.requireNonNull(credentialStore);
        this.taskClient = Objects.requireNonNull(taskClient);
        this.executorRegistry = Objects.requireNonNull(executorRegistry);
        this.currentExecutionStore = Objects.requireNonNull(currentExecutionStore);
        this.agentStateStore = Objects.requireNonNull(agentStateStore);
        this.executor = Objects.requireNonNull(executor);
        this.clock = Objects.requireNonNull(clock);
    }

    public void start(Duration interval) {
        Objects.requireNonNull(interval, "interval is required");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Task polling interval must be positive.");
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        long intervalSeconds = Math.max(1, interval.toSeconds());
        scheduledTask = executor.scheduleWithFixedDelay(
                this::executePollingSafely,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        agentStateStore.setTaskPollingEnabled(true);
        publishCurrentExecutionSummary();
        log.info("Task polling scheduler started");
    }

    public void stop() {
        boolean wasRunning = running.getAndSet(false);

        ScheduledFuture<?> currentTask = scheduledTask;
        if (currentTask != null) {
            currentTask.cancel(false);
        }

        agentStateStore.setTaskPollingEnabled(false);

        if (wasRunning) {
            log.info("Task polling scheduler stopped");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    void runPollingOnce() {
        executePollingSafely();
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }

    private void executePollingSafely() {
        if (!running.get()) {
            return;
        }

        try {
            executePolling();
        } catch (RuntimeException exception) {
            log.warn("Task polling failed: {}", exception.getMessage());
            agentStateStore.setLastError("Task polling failed: " + exception.getMessage());
        }
    }

    private void executePolling() {
        AgentConfig config = configService.load();
        AgentConfigValidator.validateWorkerApiReady(config, credentialStore.hasApiKey());
        String apiKey = credentialStore.loadApiKey()
                .orElseThrow(() -> new IllegalStateException("API key is required."));

        renewCurrentExecutionIfNeeded(config, apiKey);

        if (config.pauseEnabled()) {
            log.debug("Task polling skipped because worker is paused");
            return;
        }

        if (currentExecutionStore.hasCurrentExecution()) {
            log.debug("Task polling skipped because an execution is already in memory");
            return;
        }

        Optional<ClaimedExecutionPayload> claimedExecution = taskClient.claimNext(
                config.masterBaseUrl(),
                config.workerId(),
                apiKey
        );
        if (claimedExecution.isEmpty()) {
            return;
        }

        executeClaimedPayload(config, apiKey, claimedExecution.get());
    }

    private void renewCurrentExecutionIfNeeded(AgentConfig config, String apiKey) {
        Optional<CurrentExecution> currentExecution = currentExecutionStore.currentExecution();
        if (currentExecution.isEmpty()
                || currentExecution.get().status() != CurrentExecutionStatus.RUNNING
                || !shouldRenew(currentExecution.get(), LocalDateTime.now(clock))) {
            return;
        }

        CurrentExecution execution = currentExecution.get();
        try {
            LocalDateTime renewedLeaseExpiresAt = taskClient.renewLease(
                    config.masterBaseUrl(),
                    config.workerId(),
                    execution.executionId(),
                    apiKey,
                    execution.leaseToken()
            );
            currentExecutionStore.updateLease(renewedLeaseExpiresAt);
            publishCurrentExecutionSummary();
            log.debug("Execution lease renewed for {}", execution.executionId());
        } catch (RuntimeException exception) {
            log.warn("Execution lease renewal failed for {}: {}", execution.executionId(), exception.getMessage());
            agentStateStore.setLastError("Execution lease renewal failed: " + exception.getMessage());
        }
    }

    private void executeClaimedPayload(AgentConfig config, String apiKey, ClaimedExecutionPayload payload) {
        CurrentExecution claimed = currentExecutionStore.setClaimed(payload);
        publishCurrentExecutionSummary();

        Optional<AgentExecutor> executorCandidate = executorRegistry.findExecutor(
                payload.executorId(),
                payload.executorContractVersion()
        );

        if (executorCandidate.isEmpty()) {
            reportFailedAndClearOrKeepError(
                    config,
                    apiKey,
                    claimed,
                    UNSUPPORTED_EXECUTOR_FAILURE_CODE,
                    "Unsupported executor: " + payload.executorId() + " / " + payload.executorContractVersion()
            );
            return;
        }

        try {
            taskClient.reportRunning(
                    config.masterBaseUrl(),
                    config.workerId(),
                    claimed.executionId(),
                    apiKey,
                    claimed.leaseToken()
            );
            currentExecutionStore.markRunning();
            publishCurrentExecutionSummary();
        } catch (RuntimeException exception) {
            keepErrorState("Failed to report execution RUNNING: " + exception.getMessage());
            return;
        }

        AgentExecutionResult result = executorCandidate.get().execute(payload, new AgentExecutionContext(clock));
        if (result.success()) {
            reportSucceededAndClearOrKeepError(config, apiKey, claimed);
            return;
        }

        reportFailedAndClearOrKeepError(
                config,
                apiKey,
                claimed,
                result.failureCode(),
                result.failureMessage()
        );
    }

    private void reportSucceededAndClearOrKeepError(AgentConfig config, String apiKey, CurrentExecution execution) {
        try {
            taskClient.reportSucceeded(
                    config.masterBaseUrl(),
                    config.workerId(),
                    execution.executionId(),
                    apiKey,
                    execution.leaseToken()
            );
            currentExecutionStore.markSucceeded();
            currentExecutionStore.clear();
            publishCurrentExecutionSummary();
        } catch (RuntimeException exception) {
            keepErrorState("Failed to report execution SUCCEEDED: " + exception.getMessage());
        }
    }

    private void reportFailedAndClearOrKeepError(AgentConfig config,
                                                 String apiKey,
                                                 CurrentExecution execution,
                                                 String failureCode,
                                                 String failureMessage) {
        try {
            taskClient.reportFailed(
                    config.masterBaseUrl(),
                    config.workerId(),
                    execution.executionId(),
                    apiKey,
                    execution.leaseToken(),
                    failureCode,
                    failureMessage
            );
            currentExecutionStore.markFailed();
            currentExecutionStore.clear();
            publishCurrentExecutionSummary();
        } catch (RuntimeException exception) {
            keepErrorState("Failed to report execution FAILED: " + exception.getMessage());
        }
    }

    private void keepErrorState(String error) {
        currentExecutionStore.markError(error);
        publishCurrentExecutionSummary();
        agentStateStore.setLastError(error);
        log.error(error);
    }

    private void publishCurrentExecutionSummary() {
        agentStateStore.setCurrentExecutionSummary(currentExecutionStore.summary());
    }

    private static boolean shouldRenew(CurrentExecution execution, LocalDateTime now) {
        return Duration.between(now, execution.leaseExpiresAt()).compareTo(LEASE_RENEWAL_THRESHOLD) < 0;
    }
}
