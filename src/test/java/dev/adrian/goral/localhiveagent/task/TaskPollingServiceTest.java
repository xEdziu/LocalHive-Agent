package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.master.MasterClientException;
import dev.adrian.goral.localhiveagent.master.MasterTaskClient;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import dev.adrian.goral.localhiveagent.security.CredentialStore;
import dev.adrian.goral.localhiveagent.state.AgentStateStore;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryEntry;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryStatus;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPollingServiceTest {

    private static final UUID WORKER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID EXECUTION_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    private static final String API_KEY = "worker-api-key";
    private static final String LEASE_TOKEN = "lease-token";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());

    @TempDir
    private Path tempDir;

    @Test
    void shouldStartWithTenSecondPollingInterval() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());

        fixture.service.start(TaskPollingService.DEFAULT_POLLING_INTERVAL);

        assertTrue(fixture.service.isRunning());
        assertTrue(fixture.agentStateStore.snapshot().taskPollingEnabled());
        assertEquals(0, fixture.executor.initialDelay);
        assertEquals(10, fixture.executor.delay);
        assertEquals(TimeUnit.SECONDS, fixture.executor.unit);
        assertNotNull(fixture.executor.scheduledCommand);
    }

    @Test
    void shouldSkipClaimWhenWorkerIsPaused() {
        TestFixture fixture = createFixture(true, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.claimCalls);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
        assertEquals(0, fixture.taskHistoryStore.count());
    }

    @Test
    void shouldSkipClaimWhenApiKeyIsMissing() {
        TestFixture fixture = createFixture(false, false, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.claimCalls);
        assertTrue(fixture.agentStateStore.snapshot().lastError().contains("Task polling failed"));
    }

    @Test
    void shouldSkipClaimWhenWorkerConfigIsInvalid() {
        TestFixture fixture = createFixture(AgentConfig.empty(), true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.claimCalls);
        assertTrue(fixture.agentStateStore.snapshot().lastError().contains("Task polling failed"));
    }

    @Test
    void shouldSkipClaimWhenExecutionIsAlreadyInMemory() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.currentExecutionStore.setClaimed(noOpPayload(NOW.plusMinutes(1)));
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.claimCalls);
        assertTrue(fixture.currentExecutionStore.currentExecution().isPresent());
    }

    @Test
    void shouldKeepIdleStateWhenMasterReturnsNoExecution() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.empty();

        fixture.startAndRunOnce();

        assertEquals(1, fixture.taskClient.claimCalls);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
        assertEquals("none", fixture.agentStateStore.snapshot().currentExecutionSummary());
        assertEquals(0, fixture.taskHistoryStore.count());
    }

    @Test
    void shouldExecuteNoOpAndReportRunningThenSucceeded() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(1, fixture.taskClient.claimCalls);
        assertEquals(List.of("RUNNING", "SUCCEEDED"), fixture.taskClient.reports);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
        assertEquals("none", fixture.agentStateStore.snapshot().currentExecutionSummary());
        AgentTaskHistoryEntry history = fixture.taskHistoryStore.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(AgentTaskHistoryStatus.SUCCEEDED, history.status());
        assertEquals(1, fixture.agentStateStore.snapshot().taskHistoryCount());
        assertTrue(fixture.agentStateStore.snapshot().latestTaskHistorySummary().contains("SUCCEEDED"));
    }

    @Test
    void shouldReportFailedWhenExecutorIsUnsupported() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(payload("localhive.missing", 1, NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(List.of("FAILED"), fixture.taskClient.reports);
        assertEquals(TaskPollingService.UNSUPPORTED_EXECUTOR_FAILURE_CODE, fixture.taskClient.failureCode);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
        AgentTaskHistoryEntry history = fixture.taskHistoryStore.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(AgentTaskHistoryStatus.FAILED, history.status());
        assertEquals(TaskPollingService.UNSUPPORTED_EXECUTOR_FAILURE_CODE, history.failureCode());
    }

    @Test
    void shouldReportFailedWhenExecutorReturnsFailure() {
        AgentExecutorRegistry registry = AgentExecutorRegistry.withDefaultExecutors();
        registry.register("localhive.failing-test", 1, (payload, context) ->
                AgentExecutionResult.failed("TEST_FAILED", "boom"));
        TestFixture fixture = createFixture(false, true, registry);
        fixture.taskClient.nextClaim = Optional.of(payload("localhive.failing-test", 1, NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(List.of("RUNNING", "FAILED"), fixture.taskClient.reports);
        assertEquals("TEST_FAILED", fixture.taskClient.failureCode);
        assertEquals("boom", fixture.taskClient.failureMessage);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
    }

    @Test
    void shouldPassMasterContextToExecutor() {
        RecordingAgentExecutor executor = new RecordingAgentExecutor();
        AgentExecutorRegistry registry = AgentExecutorRegistry.withDefaultExecutors();
        registry.register("localhive.recording-test", 1, executor);
        TestFixture fixture = createFixture(false, true, registry);
        fixture.taskClient.nextClaim = Optional.of(payload("localhive.recording-test", 1, NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals("http://localhost:8080", executor.context.masterBaseUrl());
        assertEquals(WORKER_ID, executor.context.workerId());
        assertEquals(API_KEY, executor.context.apiKey());
        assertFalse(executor.context.toString().contains(API_KEY));
    }

    @Test
    void shouldKeepExecutionRunningUntilExecutorReturns() {
        FakeMasterTaskClient[] taskClientRef = new FakeMasterTaskClient[1];
        AgentExecutor executor = (payload, context) -> {
            assertEquals(List.of("RUNNING"), taskClientRef[0].reports);
            return AgentExecutionResult.succeeded();
        };
        AgentExecutorRegistry registry = AgentExecutorRegistry.withDefaultExecutors();
        registry.register("localhive.execution-order-test", 1, executor);
        TestFixture fixture = createFixture(false, true, registry);
        taskClientRef[0] = fixture.taskClient;
        fixture.taskClient.nextClaim = Optional.of(payload("localhive.execution-order-test", 1, NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(List.of("RUNNING", "SUCCEEDED"), fixture.taskClient.reports);
    }

    @Test
    void shouldReportNoOpFailureCodeWhenNoOpExecutorFails() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(new ClaimedExecutionPayload(
                EXECUTION_ID,
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                new ThrowingMap(),
                0,
                0,
                false,
                LEASE_TOKEN,
                NOW.plusMinutes(1).toString()
        ));

        fixture.startAndRunOnce();

        assertEquals(List.of("RUNNING", "FAILED"), fixture.taskClient.reports);
        assertEquals(NoOpAgentExecutor.FAILURE_CODE, fixture.taskClient.failureCode);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
    }

    @Test
    void shouldKeepErrorStateWhenRunningReportFails() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));
        fixture.taskClient.throwOnRunning = true;

        fixture.startAndRunOnce();

        CurrentExecution execution = fixture.currentExecutionStore.currentExecution().orElseThrow();
        assertEquals(CurrentExecutionStatus.ERROR, execution.status());
        assertTrue(execution.lastError().contains("Failed to report execution RUNNING"));
        assertFalse(execution.toString().contains(LEASE_TOKEN));
        AgentTaskHistoryEntry history = fixture.taskHistoryStore.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(AgentTaskHistoryStatus.ERROR, history.status());
        assertTrue(history.lastError().contains("Failed to report execution RUNNING"));
    }

    @Test
    void shouldKeepErrorStateWhenSucceededReportFails() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));
        fixture.taskClient.throwOnSucceeded = true;

        fixture.startAndRunOnce();

        CurrentExecution execution = fixture.currentExecutionStore.currentExecution().orElseThrow();
        assertEquals(CurrentExecutionStatus.ERROR, execution.status());
        assertTrue(execution.lastError().contains("Failed to report execution SUCCEEDED"));
        AgentTaskHistoryEntry history = fixture.taskHistoryStore.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(AgentTaskHistoryStatus.ERROR, history.status());
        assertTrue(history.lastError().contains("Failed to report execution SUCCEEDED"));
    }

    @Test
    void shouldHandleMasterClaimFailureWithoutCrashing() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.taskClient.throwOnClaim = true;

        fixture.startAndRunOnce();

        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
        assertTrue(fixture.agentStateStore.snapshot().lastError().contains("Task polling failed"));
    }

    @Test
    void shouldRenewRunningExecutionLeaseNearExpiration() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.currentExecutionStore.setClaimed(noOpPayload(NOW.plusSeconds(10)));
        fixture.currentExecutionStore.markRunning();
        fixture.taskClient.renewedLeaseExpiresAt = NOW.plusMinutes(5);

        fixture.startAndRunOnce();

        CurrentExecution execution = fixture.currentExecutionStore.currentExecution().orElseThrow();
        assertEquals(1, fixture.taskClient.renewCalls);
        assertEquals(NOW.plusMinutes(5), execution.leaseExpiresAt());
        assertEquals(0, fixture.taskClient.claimCalls);
    }

    @Test
    void shouldNotRenewRunningExecutionLeaseWhenExpirationIsNotNear() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.currentExecutionStore.setClaimed(noOpPayload(NOW.plusSeconds(25)));
        fixture.currentExecutionStore.markRunning();

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.renewCalls);
        assertEquals(0, fixture.taskClient.claimCalls);
    }

    @Test
    void shouldContinueTaskPipelineWhenHistoryStoreFails() {
        TestFixture fixture = createFixture(
                false,
                true,
                AgentExecutorRegistry.withDefaultExecutors(),
                new FailingTaskHistoryStore(tempDir.resolve("failing-history.sqlite"))
        );
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(List.of("RUNNING", "SUCCEEDED"), fixture.taskClient.reports);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
    }

    @Test
    void shouldNotRenewClaimedExecution() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.currentExecutionStore.setClaimed(noOpPayload(NOW.plusSeconds(10)));

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.renewCalls);
        assertEquals(0, fixture.taskClient.claimCalls);
    }

    @Test
    void shouldNotRenewSucceededExecution() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.currentExecutionStore.setClaimed(noOpPayload(NOW.plusSeconds(10)));
        fixture.currentExecutionStore.markSucceeded();

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.renewCalls);
        assertEquals(0, fixture.taskClient.claimCalls);
    }

    @Test
    void shouldNotRenewFailedExecution() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.currentExecutionStore.setClaimed(noOpPayload(NOW.plusSeconds(10)));
        fixture.currentExecutionStore.markFailed();

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.renewCalls);
        assertEquals(0, fixture.taskClient.claimCalls);
    }

    @Test
    void shouldNotRenewErrorExecution() {
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors());
        fixture.currentExecutionStore.setClaimed(noOpPayload(NOW.plusSeconds(10)));
        fixture.currentExecutionStore.markError("terminal report failed");

        fixture.startAndRunOnce();

        assertEquals(0, fixture.taskClient.renewCalls);
        assertEquals(0, fixture.taskClient.claimCalls);
    }

    @Test
    void shouldDelegateExecutionToDedicatedWorkerAndAvoidSecondClaimWhileActive() {
        ManualExecutorService executionWorker = new ManualExecutorService();
        TestFixture fixture = createFixture(false, true, AgentExecutorRegistry.withDefaultExecutors(), executionWorker);
        fixture.taskClient.nextClaim = Optional.of(noOpPayload(NOW.plusMinutes(1)));

        fixture.startAndRunOnce();

        assertEquals(1, fixture.taskClient.claimCalls);
        assertEquals(1, executionWorker.pendingCommands());
        assertTrue(fixture.taskClient.reports.isEmpty());
        assertEquals(CurrentExecutionStatus.CLAIMED, fixture.currentExecutionStore.currentExecution().orElseThrow().status());

        fixture.service.runPollingOnce();

        assertEquals(1, fixture.taskClient.claimCalls);

        executionWorker.runNext();

        assertEquals(List.of("RUNNING", "SUCCEEDED"), fixture.taskClient.reports);
        assertTrue(fixture.currentExecutionStore.currentExecution().isEmpty());
    }

    private TestFixture createFixture(boolean paused, boolean apiKeyPresent, AgentExecutorRegistry registry) {
        return createFixture(new AgentConfig(
                "http://localhost:8080",
                WORKER_ID,
                4096,
                paused
        ), apiKeyPresent, registry);
    }

    private TestFixture createFixture(boolean paused,
                                      boolean apiKeyPresent,
                                      AgentExecutorRegistry registry,
                                      AgentTaskHistoryStore taskHistoryStore) {
        return createFixture(new AgentConfig(
                "http://localhost:8080",
                WORKER_ID,
                4096,
                paused
        ), apiKeyPresent, registry, taskHistoryStore);
    }

    private TestFixture createFixture(AgentConfig config, boolean apiKeyPresent, AgentExecutorRegistry registry) {
        AgentTaskHistoryStore taskHistoryStore = new AgentTaskHistoryStore(tempDir.resolve("task-history.sqlite"));
        taskHistoryStore.initialize();
        return createFixture(config, apiKeyPresent, registry, taskHistoryStore);
    }

    private TestFixture createFixture(boolean paused,
                                      boolean apiKeyPresent,
                                      AgentExecutorRegistry registry,
                                      ExecutorService executionWorker) {
        AgentTaskHistoryStore taskHistoryStore = new AgentTaskHistoryStore(tempDir.resolve("task-history.sqlite"));
        taskHistoryStore.initialize();
        return createFixture(new AgentConfig(
                "http://localhost:8080",
                WORKER_ID,
                4096,
                paused
        ), apiKeyPresent, registry, taskHistoryStore, executionWorker);
    }

    private TestFixture createFixture(AgentConfig config,
                                      boolean apiKeyPresent,
                                      AgentExecutorRegistry registry,
                                      AgentTaskHistoryStore taskHistoryStore) {
        return createFixture(config, apiKeyPresent, registry, taskHistoryStore, new DirectExecutorService());
    }

    private TestFixture createFixture(AgentConfig config,
                                      boolean apiKeyPresent,
                                      AgentExecutorRegistry registry,
                                      AgentTaskHistoryStore taskHistoryStore,
                                      ExecutorService executionWorker) {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));
        configService.save(config);

        CurrentExecutionStore currentExecutionStore = new CurrentExecutionStore();
        AgentStateStore agentStateStore = AgentStateStore.fromConfig(configService.load(), apiKeyPresent, false);
        FakeMasterTaskClient taskClient = new FakeMasterTaskClient();
        RecordingScheduledExecutorService executor = new RecordingScheduledExecutorService();
        TaskPollingService service = new TaskPollingService(
                configService,
                new StaticCredentialStore(apiKeyPresent ? API_KEY : null),
                taskClient,
                registry,
                currentExecutionStore,
                taskHistoryStore,
                agentStateStore,
                executor,
                executionWorker,
                CLOCK
        );

        return new TestFixture(service, taskClient, currentExecutionStore, taskHistoryStore, agentStateStore, executor);
    }

    private static ClaimedExecutionPayload noOpPayload(LocalDateTime leaseExpiresAt) {
        return payload(
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                leaseExpiresAt
        );
    }

    private static ClaimedExecutionPayload payload(String executorId, int executorContractVersion, LocalDateTime leaseExpiresAt) {
        return new ClaimedExecutionPayload(
                EXECUTION_ID,
                executorId,
                executorContractVersion,
                Map.of("message", "hello"),
                0,
                0,
                false,
                LEASE_TOKEN,
                leaseExpiresAt.toString()
        );
    }

    private record TestFixture(
            TaskPollingService service,
            FakeMasterTaskClient taskClient,
            CurrentExecutionStore currentExecutionStore,
            AgentTaskHistoryStore taskHistoryStore,
            AgentStateStore agentStateStore,
            RecordingScheduledExecutorService executor
    ) {

        private void startAndRunOnce() {
            service.start(TaskPollingService.DEFAULT_POLLING_INTERVAL);
            service.runPollingOnce();
        }
    }

    private static final class FailingTaskHistoryStore extends AgentTaskHistoryStore {

        private FailingTaskHistoryStore(Path databasePath) {
            super(databasePath);
        }

        @Override
        public void initialize() {
        }

        @Override
        public void recordClaimed(UUID executionId,
                                  String executorId,
                                  int executorContractVersion,
                                  Instant claimedAt) {
            throw new IllegalStateException("history unavailable");
        }

        @Override
        public void recordRunning(UUID executionId, Instant startedAt) {
            throw new IllegalStateException("history unavailable");
        }

        @Override
        public void recordSucceeded(UUID executionId, Instant completedAt) {
            throw new IllegalStateException("history unavailable");
        }

        @Override
        public List<AgentTaskHistoryEntry> findLatest(int limit) {
            throw new IllegalStateException("history unavailable");
        }

        @Override
        public long count() {
            throw new IllegalStateException("history unavailable");
        }
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
            return Optional.ofNullable(apiKey);
        }

        @Override
        public void deleteApiKey() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeMasterTaskClient extends MasterTaskClient {

        private Optional<ClaimedExecutionPayload> nextClaim = Optional.empty();
        private final List<String> reports = new ArrayList<>();
        private int claimCalls;
        private int renewCalls;
        private LocalDateTime renewedLeaseExpiresAt = NOW.plusMinutes(1);
        private String failureCode;
        private String failureMessage;
        private boolean throwOnClaim;
        private boolean throwOnRunning;
        private boolean throwOnSucceeded;

        @Override
        public Optional<ClaimedExecutionPayload> claimNext(String masterBaseUrl, UUID workerId, String apiKey) {
            claimCalls++;
            if (throwOnClaim) {
                throw new MasterClientException("claim failed");
            }
            return nextClaim;
        }

        @Override
        public void reportRunning(String masterBaseUrl,
                                  UUID workerId,
                                  UUID executionId,
                                  String apiKey,
                                  String leaseToken) {
            if (throwOnRunning) {
                throw new MasterClientException("running failed");
            }
            reports.add("RUNNING");
        }

        @Override
        public void reportSucceeded(String masterBaseUrl,
                                    UUID workerId,
                                    UUID executionId,
                                    String apiKey,
                                    String leaseToken) {
            if (throwOnSucceeded) {
                throw new MasterClientException("succeeded failed");
            }
            reports.add("SUCCEEDED");
        }

        @Override
        public void reportFailed(String masterBaseUrl,
                                 UUID workerId,
                                 UUID executionId,
                                 String apiKey,
                                 String leaseToken,
                                 String failureCode,
                                 String failureMessage) {
            reports.add("FAILED");
            this.failureCode = failureCode;
            this.failureMessage = failureMessage;
        }

        @Override
        public LocalDateTime renewLease(String masterBaseUrl,
                                        UUID workerId,
                                        UUID executionId,
                                        String apiKey,
                                        String leaseToken) {
            renewCalls++;
            return renewedLeaseExpiresAt;
        }
    }

    private static final class ThrowingMap extends java.util.HashMap<String, Object> {

        @Override
        public Object get(Object key) {
            throw new IllegalStateException("configuration unavailable");
        }
    }

    private static final class RecordingAgentExecutor implements AgentExecutor {

        private AgentExecutionContext context;

        @Override
        public AgentExecutionResult execute(ClaimedExecutionPayload payload, AgentExecutionContext context) {
            this.context = context;
            return AgentExecutionResult.succeeded();
        }
    }

    private static final class RecordingScheduledExecutorService
            extends AbstractExecutorService implements ScheduledExecutorService {

        private final RecordingScheduledFuture future = new RecordingScheduledFuture();
        private Runnable scheduledCommand;
        private long initialDelay;
        private long delay;
        private TimeUnit unit;
        private boolean shutdown;

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit
        ) {
            this.scheduledCommand = command;
            this.initialDelay = initialDelay;
            this.delay = delay;
            this.unit = unit;
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

    private static final class DirectExecutorService extends AbstractExecutorService {

        private boolean shutdown;

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
    }

    private static final class ManualExecutorService extends AbstractExecutorService {

        private final List<Runnable> commands = new ArrayList<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = List.copyOf(commands);
            commands.clear();
            return pending;
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
            commands.add(command);
        }

        private int pendingCommands() {
            return commands.size();
        }

        private void runNext() {
            commands.removeFirst().run();
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
