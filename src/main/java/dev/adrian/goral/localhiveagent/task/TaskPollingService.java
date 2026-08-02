package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.app.AgentPaths;
import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.master.MasterClientException;
import dev.adrian.goral.localhiveagent.master.MasterTaskClient;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import dev.adrian.goral.localhiveagent.security.CredentialStore;
import dev.adrian.goral.localhiveagent.state.AgentStateStore;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryEntry;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryStore;
import dev.adrian.goral.localhiveagent.validation.AgentConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
    private final AgentTaskHistoryStore taskHistoryStore;
    private final AgentStateStore agentStateStore;
    private final ScheduledExecutorService pollingExecutor;
    private final ExecutorService executionWorker;
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
                new AgentTaskHistoryStore(AgentPaths.taskHistoryPath()),
                agentStateStore
        );
    }

    public TaskPollingService(ConfigService configService,
                              CredentialStore credentialStore,
                              MasterTaskClient taskClient,
                              AgentExecutorRegistry executorRegistry,
                              CurrentExecutionStore currentExecutionStore,
                              AgentTaskHistoryStore taskHistoryStore,
                              AgentStateStore agentStateStore) {
        this(
                configService,
                credentialStore,
                taskClient,
                executorRegistry,
                currentExecutionStore,
                taskHistoryStore,
                agentStateStore,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "localhive-task-polling");
                    thread.setDaemon(true);
                    return thread;
                }),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "localhive-task-execution");
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
                       AgentTaskHistoryStore taskHistoryStore,
                       AgentStateStore agentStateStore,
                       ScheduledExecutorService pollingExecutor,
                       ExecutorService executionWorker,
                       Clock clock) {
        this.configService = Objects.requireNonNull(configService);
        this.credentialStore = Objects.requireNonNull(credentialStore);
        this.taskClient = Objects.requireNonNull(taskClient);
        this.executorRegistry = Objects.requireNonNull(executorRegistry);
        this.currentExecutionStore = Objects.requireNonNull(currentExecutionStore);
        this.taskHistoryStore = Objects.requireNonNull(taskHistoryStore);
        this.agentStateStore = Objects.requireNonNull(agentStateStore);
        this.pollingExecutor = Objects.requireNonNull(pollingExecutor);
        this.executionWorker = Objects.requireNonNull(executionWorker);
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
        scheduledTask = pollingExecutor.scheduleWithFixedDelay(
                this::executePollingSafely,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        agentStateStore.setTaskPollingEnabled(true);
        publishCurrentExecutionSummary();
        publishTaskHistorySummary();
        log.info("Task polling started. Interval: {}s", intervalSeconds);
    }

    public void stop() {
        boolean wasRunning = running.getAndSet(false);

        ScheduledFuture<?> currentTask = scheduledTask;
        if (currentTask != null) {
            currentTask.cancel(false);
        }

        agentStateStore.setTaskPollingEnabled(false);

        if (wasRunning) {
            log.info("Task polling stopped.");
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
        pollingExecutor.shutdownNow();
        executionWorker.shutdownNow();
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
        try {
            AgentConfigValidator.validateWorkerApiReady(config, credentialStore.hasApiKey());
        } catch (RuntimeException exception) {
            log.warn("Task claim skipped because config invalid: {}", exception.getMessage());
            throw exception;
        }
        String apiKey = credentialStore.loadApiKey()
                .orElseThrow(() -> new IllegalStateException("API key is required."));

        renewCurrentExecutionIfNeeded(config, apiKey);

        if (config.pauseEnabled()) {
            log.warn("Task claim skipped because Agent is paused.");
            return;
        }

        if (currentExecutionStore.hasCurrentExecution()) {
            currentExecutionStore.currentExecution().ifPresent(execution ->
                    log.warn(
                            "Task claim skipped because current execution is still active. executionId={} status={}",
                            execution.executionId(),
                            execution.status()
                    ));
            return;
        }

        Optional<ClaimedExecutionPayload> claimedExecution;
        try {
            claimedExecution = taskClient.claimNext(
                    config.masterBaseUrl(),
                    config.workerId(),
                    apiKey
            );
        } catch (RuntimeException exception) {
            logClaimFailure(exception);
            throw exception;
        }

        if (claimedExecution.isEmpty()) {
            log.info("No assigned execution found.");
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
            log.info(
                    "Lease renewed for execution {}. leaseExpiresAt={}",
                    execution.executionId(),
                    renewedLeaseExpiresAt
            );
        } catch (RuntimeException exception) {
            logLeaseRenewalFailure(execution, exception);
            agentStateStore.setLastError("Execution lease renewal failed: " + exception.getMessage());
        }
    }

    private void executeClaimedPayload(AgentConfig config, String apiKey, ClaimedExecutionPayload payload) {
        CurrentExecution claimed = currentExecutionStore.setClaimed(payload);
        publishCurrentExecutionSummary();
        recordTaskHistory("record claimed execution", () ->
                taskHistoryStore.recordClaimed(
                        payload.executionId(),
                        claimed.displayName(),
                        payload.executorId(),
                        payload.executorContractVersion(),
                        clock.instant()
                ));
        log.info(
                "Claimed execution {} displayName=\"{}\" executor={}/{} leaseExpiresAt={}",
                claimed.executionId(),
                claimed.displayName(),
                claimed.executorId(),
                claimed.executorContractVersion(),
                claimed.leaseExpiresAt()
        );

        Optional<AgentExecutor> executorCandidate = executorRegistry.findExecutor(
                payload.executorId(),
                payload.executorContractVersion()
        );

        if (executorCandidate.isEmpty()) {
            log.warn(
                    "Unsupported executor {}/{} for execution {}.",
                    payload.executorId(),
                    payload.executorContractVersion(),
                    payload.executionId()
            );
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
            executionWorker.execute(() -> runSupportedExecutionSafely(
                    config,
                    apiKey,
                    payload,
                    claimed,
                    executorCandidate.get()
            ));
            log.info(
                    "Execution {} delegated to dedicated worker for executor {}/{}.",
                    payload.executionId(),
                    payload.executorId(),
                    payload.executorContractVersion()
            );
        } catch (RejectedExecutionException exception) {
            keepErrorState(
                    "Failed to start execution worker: " + exception.getMessage(),
                    claimed.executionId(),
                    "Execution worker rejected claimed execution."
            );
        }
    }

    private void runSupportedExecutionSafely(AgentConfig config,
                                             String apiKey,
                                             ClaimedExecutionPayload payload,
                                             CurrentExecution claimed,
                                             AgentExecutor executor) {
        try {
            runSupportedExecution(config, apiKey, payload, claimed, executor);
        } catch (RuntimeException exception) {
            log.error(
                    "Executor execution failed unexpectedly. executionId={} executor={}/{} errorType={}",
                    payload.executionId(),
                    payload.executorId(),
                    payload.executorContractVersion(),
                    exception.getClass().getSimpleName()
            );
            keepErrorState(
                    "Executor execution failed unexpectedly: " + exception.getMessage(),
                    claimed.executionId(),
                    "Executor execution failed unexpectedly. Current execution moved to ERROR."
            );
        }
    }

    private void runSupportedExecution(AgentConfig config,
                                       String apiKey,
                                       ClaimedExecutionPayload payload,
                                       CurrentExecution claimed,
                                       AgentExecutor executor) {
        try {
            long reportStartedNanos = System.nanoTime();
            log.info("Reporting RUNNING for execution {}.", claimed.executionId());
            taskClient.reportRunning(
                    config.masterBaseUrl(),
                    config.workerId(),
                    claimed.executionId(),
                    apiKey,
                    claimed.leaseToken()
            );
            currentExecutionStore.markRunning();
            publishCurrentExecutionSummary();
            recordTaskHistory("record running execution", () ->
                    taskHistoryStore.recordRunning(claimed.executionId(), clock.instant()));
            log.info(
                    "Execution {} reported RUNNING. durationMs={}",
                    claimed.executionId(),
                    elapsedMillis(reportStartedNanos)
            );
        } catch (RuntimeException exception) {
            keepErrorState(
                    "Failed to report execution RUNNING: " + exception.getMessage(),
                    claimed.executionId(),
                    "Failed to report RUNNING. Current execution moved to ERROR."
            );
            return;
        }

        AgentExecutionResult result;
        long executorStartedNanos = System.nanoTime();
        log.info(
                "Executing {}/{} for execution {}.",
                payload.executorId(),
                payload.executorContractVersion(),
                payload.executionId()
        );
        result = executor.execute(
                payload,
                new AgentExecutionContext(clock, config.masterBaseUrl(), config.workerId(), apiKey)
        );

        if (result.success()) {
            log.info(
                    "Executor {}/{} completed successfully for execution {}. durationMs={}",
                    payload.executorId(),
                    payload.executorContractVersion(),
                    payload.executionId(),
                    elapsedMillis(executorStartedNanos)
            );
            reportSucceededAndClearOrKeepError(config, apiKey, claimed);
            return;
        }

        log.warn(
                "Executor {}/{} returned failure for execution {}. failureCode={}",
                payload.executorId(),
                payload.executorContractVersion(),
                payload.executionId(),
                result.failureCode()
        );
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
            long reportStartedNanos = System.nanoTime();
            log.info("Reporting SUCCEEDED for execution {}.", execution.executionId());
            taskClient.reportSucceeded(
                    config.masterBaseUrl(),
                    config.workerId(),
                    execution.executionId(),
                    apiKey,
                    execution.leaseToken()
            );
            log.info(
                    "Execution {} reported SUCCEEDED. durationMs={}",
                    execution.executionId(),
                    elapsedMillis(reportStartedNanos)
            );
            recordTaskHistory("record succeeded execution", () ->
                    taskHistoryStore.recordSucceeded(execution.executionId(), clock.instant()));
            currentExecutionStore.markSucceeded();
            currentExecutionStore.clear();
            publishCurrentExecutionSummary();
            log.info("Current execution cleared after successful terminal report. executionId={}", execution.executionId());
            requestImmediatePollingAfterTerminalReport(execution.executionId());
        } catch (RuntimeException exception) {
            keepErrorState(
                    "Failed to report execution SUCCEEDED: " + exception.getMessage(),
                    execution.executionId(),
                    "Failed to report SUCCEEDED. Terminal report failed and current execution moved to ERROR."
            );
        }
    }

    private void reportFailedAndClearOrKeepError(AgentConfig config,
                                                 String apiKey,
                                                 CurrentExecution execution,
                                                 String failureCode,
                                                 String failureMessage) {
        try {
            long reportStartedNanos = System.nanoTime();
            log.info(
                    "Reporting FAILED for execution {}. failureCode={}",
                    execution.executionId(),
                    failureCode
            );
            taskClient.reportFailed(
                    config.masterBaseUrl(),
                    config.workerId(),
                    execution.executionId(),
                    apiKey,
                    execution.leaseToken(),
                    failureCode,
                    failureMessage
            );
            log.info(
                    "Execution {} reported FAILED. durationMs={}",
                    execution.executionId(),
                    elapsedMillis(reportStartedNanos)
            );
            recordTaskHistory("record failed execution", () ->
                    taskHistoryStore.recordFailed(
                            execution.executionId(),
                            failureCode,
                            failureMessage,
                            clock.instant()
                    ));
            currentExecutionStore.markFailed();
            currentExecutionStore.clear();
            publishCurrentExecutionSummary();
            log.info("Current execution cleared after failed report success. executionId={}", execution.executionId());
            requestImmediatePollingAfterTerminalReport(execution.executionId());
        } catch (RuntimeException exception) {
            keepErrorState(
                    "Failed to report execution FAILED: " + exception.getMessage(),
                    execution.executionId(),
                    "Failed to report FAILED. Terminal report failed and current execution moved to ERROR."
            );
        }
    }

    private void requestImmediatePollingAfterTerminalReport(UUID executionId) {
        if (!running.get()) {
            return;
        }

        try {
            pollingExecutor.execute(this::executePollingSafely);
            log.info(
                    "Execution {} completed and reported; checking for another assignment immediately.",
                    executionId
            );
        } catch (RejectedExecutionException exception) {
            if (running.get()) {
                log.warn(
                        "Immediate task polling request rejected after execution {} completed: {}",
                        executionId,
                        exception.getMessage()
                );
            }
        }
    }

    private void keepErrorState(String error, UUID executionId, String logMessage) {
        currentExecutionStore.markError(error);
        publishCurrentExecutionSummary();
        recordTaskHistory("record error execution", () ->
                taskHistoryStore.recordError(executionId, error, clock.instant()));
        agentStateStore.setLastError(error);
        log.error("{} executionId={} reason={}", logMessage, executionId, error);
    }

    private void publishCurrentExecutionSummary() {
        agentStateStore.setCurrentExecutionSummary(currentExecutionStore.summary());
    }

    private void recordTaskHistory(String action, Runnable recorder) {
        try {
            recorder.run();
            publishTaskHistorySummary();
        } catch (RuntimeException exception) {
            log.warn("Task history write failed during {}: {}", action, exception.getMessage());
        }
    }

    private void publishTaskHistorySummary() {
        try {
            long count = taskHistoryStore.count();
            String latestSummary = taskHistoryStore.findLatest(1).stream()
                    .findFirst()
                    .map(AgentTaskHistoryEntry::summary)
                    .orElse("none");
            agentStateStore.setTaskHistory(count, latestSummary);
        } catch (RuntimeException exception) {
            log.warn("Task history summary refresh failed: {}", exception.getMessage());
        }
    }

    private static boolean shouldRenew(CurrentExecution execution, LocalDateTime now) {
        return Duration.between(now, execution.leaseExpiresAt()).compareTo(LEASE_RENEWAL_THRESHOLD) < 0;
    }

    private static void logClaimFailure(RuntimeException exception) {
        if (isMasterUnavailable(exception)) {
            log.warn(
                    "Master unavailable during claim-next. Will retry on next polling cycle. {}",
                    masterStatus(exception)
            );
            return;
        }

        log.warn(
                "Task claim failed during claim-next. Will retry on next polling cycle. message={}",
                exception.getMessage()
        );
    }

    private static void logLeaseRenewalFailure(CurrentExecution execution, RuntimeException exception) {
        if (exception instanceof MasterClientException masterException && masterException.statusCode() > 0) {
            log.warn(
                    "Lease renewal skipped or failed because Master rejected request. executionId={} httpStatus={} message={}",
                    execution.executionId(),
                    masterException.statusCode(),
                    exception.getMessage()
            );
            return;
        }

        log.warn(
                "Execution lease renewal failed for {}: {}",
                execution.executionId(),
                exception.getMessage()
        );
    }

    private static boolean isMasterUnavailable(RuntimeException exception) {
        if (exception instanceof MasterClientException masterException) {
            return masterException.statusCode() == -1 || masterException.statusCode() >= 500;
        }

        return false;
    }

    private static String masterStatus(RuntimeException exception) {
        if (exception instanceof MasterClientException masterException && masterException.statusCode() > 0) {
            return "httpStatus=" + masterException.statusCode();
        }

        return "httpStatus=unavailable";
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
