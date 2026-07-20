package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.app.AgentRuntime;
import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.heartbeat.AgentCapabilityReporter;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatTickResult;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationResult;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatRequest;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatResponse;
import dev.adrian.goral.localhiveagent.master.dto.WorkerHardwareUpdateRequest;
import dev.adrian.goral.localhiveagent.state.AgentStateListener;
import dev.adrian.goral.localhiveagent.state.AgentStateSnapshot;
import dev.adrian.goral.localhiveagent.state.AgentStateStore;
import dev.adrian.goral.localhiveagent.state.HeartbeatState;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import dev.adrian.goral.localhiveagent.task.CurrentExecution;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryEntry;
import dev.adrian.goral.localhiveagent.validation.AgentConfigValidator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class AgentMainController {

    private static final Logger log = LoggerFactory.getLogger(AgentMainController.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final Duration TASK_POLLING_INTERVAL = Duration.ofSeconds(10);

    private final AgentRuntime runtime;
    private final AgentMainView view;
    private final AgentStateStore agentStateStore;
    private final AgentStateListener viewStateListener;
    private final AtomicBoolean workerModeChangeInProgress = new AtomicBoolean(false);

    public AgentMainController(AgentRuntime runtime, AgentMainView view) {
        this.runtime = Objects.requireNonNull(runtime);
        this.view = Objects.requireNonNull(view);
        this.agentStateStore = runtime.agentStateStore();
        this.viewStateListener = this::applyStateToView;
    }

    public void initialize() {
        wireViewActions();
        agentStateStore.addListener(viewStateListener);
        applyStateToView(agentStateStore.snapshot());

        AgentConfig config = runtime.configService().loadOrCreate();
        syncConfigurationState(config);
        view.refreshConfig(config, hasStoredApiKey());
        refreshActionButtonState(config);
        autoStartHeartbeatIfReady(config);
        autoStartTaskPollingIfReady(config);
    }

    public void toggleWorkerMode() {
        togglePauseMode();
    }

    private void wireViewActions() {
        view.saveConfigButton().setOnAction(event -> saveConfigFromFields());
        view.updateAllocationButton().setOnAction(event -> updateAllocation());
        view.pauseResumeButton().setOnAction(event -> toggleWorkerMode());
        view.registerButton().setOnAction(event -> registerWithMaster());
        view.heartbeatNowButton().setOnAction(event -> sendHeartbeatNow());
        view.startHeartbeatButton().setOnAction(event -> startHeartbeat());
        view.stopHeartbeatButton().setOnAction(event -> stopHeartbeat());
        view.updateHardwareSpecButton().setOnAction(event -> updateHardwareSpec());
    }

    private boolean saveConfigFromFields() {
        try {
            AgentConfig updatedConfig = saveConfigFromFieldsInternal();

            refreshConfigLabels(updatedConfig);

            boolean apiKeyWasProvided = !view.apiKeyInput().isBlank();

            if (apiKeyWasProvided) {
                view.clearApiKeyInput();
            }

            agentStateStore.setLastMessage("Config saved.");
            autoStartHeartbeatIfReady(updatedConfig);
            autoStartTaskPollingIfReady(updatedConfig);

            return true;
        } catch (RuntimeException exception) {
            log.warn("Failed to save config", exception);
            agentStateStore.setLastError("Failed to save config: " + exception.getMessage());
            return false;
        }
    }

    private AgentConfig saveConfigFromFieldsInternal() {
        String masterBaseUrl = view.masterBaseUrlInput();
        String apiKey = view.apiKeyInput();

        int sharedRamMb = AgentConfigValidator.parseSharedRamMb(
                view.sharedRamMbInput(),
                view.detectedTotalRamMb()
        );

        if (apiKey != null && !apiKey.isBlank()) {
            runtime.credentialStore().saveApiKey(apiKey);
        }

        return runtime.configService().update(config -> config
                .withMasterBaseUrl(masterBaseUrl)
                .withSharedRamMb(sharedRamMb)
        );
    }

    private void registerWithMaster() {
        if (!saveConfigFromFields()) {
            return;
        }

        view.registerButton().setDisable(true);
        agentStateStore.setLastMessage("Registering with Master...");

        Task<AgentRegistrationResult> task = new Task<>() {
            @Override
            protected AgentRegistrationResult call() {
                return runtime.agentRegistrationService().registerCurrentMachine();
            }
        };

        task.setOnSucceeded(event -> {
            AgentRegistrationResult result = task.getValue();

            refreshConfigLabels(result.updatedConfig());

            agentStateStore.markMasterConnected("Registration completed: " + result.response().message());
            view.registerButton().setDisable(true);

            log.info("Worker registered successfully. Worker ID: {}", result.updatedConfig().workerId());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            agentStateStore.setLastError("Registration failed: " + exception.getMessage());
            view.registerButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());

            log.warn("Worker registration failed", exception);
        });

        runtime.backgroundExecutor().submit(task);
    }

    private void sendHeartbeatNow() {
        if (!saveConfigFromFields()) {
            return;
        }

        view.heartbeatNowButton().setDisable(true);
        agentStateStore.setLastMessage("Sending heartbeat...");

        Task<HeartbeatTickResult> task = new Task<>() {
            @Override
            protected HeartbeatTickResult call() {
                return executeHeartbeatOnce();
            }
        };

        task.setOnSucceeded(event -> {
            HeartbeatTickResult result = task.getValue();

            handleHeartbeatResult(result);
            view.heartbeatNowButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            agentStateStore.setLastError("Heartbeat failed: " + exception.getMessage());
            view.heartbeatNowButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());

            log.warn("Heartbeat failed", exception);
        });

        runtime.backgroundExecutor().submit(task);
    }

    private void startHeartbeat() {
        if (!saveConfigFromFields()) {
            return;
        }

        try {
            AgentConfig config = runtime.configService().load();
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());

            agentStateStore.setHeartbeatState(HeartbeatState.STARTING);
            runtime.heartbeatScheduler().start(HEARTBEAT_INTERVAL, result ->
                    logHeartbeatResult(result)
            );

            refreshActionButtonState(config);
            agentStateStore.setHeartbeatState(HeartbeatState.RUNNING);
            agentStateStore.setLastMessage("Heartbeat scheduler started.");
        } catch (RuntimeException exception) {
            runtime.heartbeatScheduler().stop();
            refreshActionButtonState(runtime.configService().load());

            log.warn("Failed to start heartbeat scheduler", exception);
            agentStateStore.setHeartbeatState(HeartbeatState.STOPPED);
            agentStateStore.setLastError("Failed to start heartbeat scheduler: " + exception.getMessage());
        }
    }

    private void stopHeartbeat() {
        runtime.heartbeatScheduler().stop();

        refreshActionButtonState(runtime.configService().load());
        agentStateStore.setHeartbeatState(HeartbeatState.STOPPED);
        agentStateStore.setLastMessage("Heartbeat scheduler stopped.");
    }

    private void updateAllocation() {
        if (!saveConfigFromFields()) {
            return;
        }

        AgentConfig config = runtime.configService().load();

        try {
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());
        } catch (RuntimeException exception) {
            agentStateStore.setLastError("Cannot update allocation: " + exception.getMessage());
            return;
        }

        view.updateAllocationButton().setDisable(true);
        agentStateStore.setLastMessage("Updating allocation...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                AgentConfig currentConfig = runtime.configService().load();

                runtime.registrationClient().updateAllocation(
                        currentConfig.masterBaseUrl(),
                        currentConfig.workerId(),
                        loadRequiredApiKey(),
                        currentConfig.sharedRamMb()
                );

                return null;
            }
        };

        task.setOnSucceeded(event -> {
            agentStateStore.markMasterConnected("Allocation updated: "
                    + runtime.configService().load().sharedRamMb() + " MB");
            view.updateAllocationButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            agentStateStore.setLastError("Allocation update failed: " + exception.getMessage());
            view.updateAllocationButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());

            log.warn("Allocation update failed", exception);
        });

        runtime.backgroundExecutor().submit(task);
    }

    private void togglePauseMode() {
        if (!workerModeChangeInProgress.compareAndSet(false, true)) {
            agentStateStore.setLastMessage("Gamer Mode change is already in progress.");
            return;
        }

        if (!saveConfigFromFields()) {
            workerModeChangeInProgress.set(false);
            return;
        }

        AgentConfig currentConfig = runtime.configService().load();

        try {
            AgentConfigValidator.validateWorkerApiReady(currentConfig, hasStoredApiKey());
        } catch (RuntimeException exception) {
            agentStateStore.setLastError("Cannot change Gamer Mode: " + exception.getMessage());
            workerModeChangeInProgress.set(false);
            return;
        }

        boolean previousPauseState = currentConfig.pauseEnabled();
        boolean newPauseState = !previousPauseState;

        AgentConfig updatedConfig = runtime.configService().update(config -> config.withPauseEnabled(newPauseState));
        refreshConfigLabels(updatedConfig);

        view.pauseResumeButton().setDisable(true);
        agentStateStore.setLastMessage(newPauseState ? "Enabling Gamer Mode..." : "Disabling Gamer Mode...");

        Task<HeartbeatTickResult> task = new Task<>() {
            @Override
            protected HeartbeatTickResult call() {
                return executeHeartbeatOnce();
            }
        };

        task.setOnSucceeded(event -> {
            HeartbeatTickResult result = task.getValue();

            if (result.success()) {
                handleHeartbeatResult(result);
                agentStateStore.setLastMessage(newPauseState
                        ? "Gamer Mode enabled. Worker is paused."
                        : "Gamer Mode disabled. Worker is active.");
            } else {
                AgentConfig rolledBackConfig = runtime.configService()
                        .update(config -> config.withPauseEnabled(previousPauseState));

                refreshConfigLabels(rolledBackConfig);

                agentStateStore.recordHeartbeatFailure("Gamer Mode change failed: " + result.error().getMessage());
                log.warn("Worker mode update failed");
                log.warn("Gamer Mode change failed", result.error());
                log.info("Worker mode rollback completed");
            }

            view.pauseResumeButton().setDisable(false);
            workerModeChangeInProgress.set(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            AgentConfig rolledBackConfig = runtime.configService()
                    .update(config -> config.withPauseEnabled(previousPauseState));

            refreshConfigLabels(rolledBackConfig);

            agentStateStore.setLastError("Gamer Mode change failed: " + exception.getMessage());
            view.pauseResumeButton().setDisable(false);
            workerModeChangeInProgress.set(false);
            refreshActionButtonState(rolledBackConfig);

            log.warn("Worker mode update failed");
            log.warn("Gamer Mode change failed", exception);
            log.info("Worker mode rollback completed");
        });

        runtime.backgroundExecutor().submit(task);
    }

    private HeartbeatTickResult executeHeartbeatOnce() {
        try {
            AgentConfig config = runtime.configService().load();
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());

            HeartbeatRequest request = new HeartbeatRequest(
                    config.pauseEnabled(),
                    config.sharedRamMb(),
                    AgentCapabilityReporter.currentCapabilities(config.docker())
            );

            HeartbeatResponse response = runtime.registrationClient().sendHeartbeat(
                    config.masterBaseUrl(),
                    config.workerId(),
                    loadRequiredApiKey(),
                    request
            );

            return HeartbeatTickResult.success(response.status());
        } catch (RuntimeException exception) {
            return HeartbeatTickResult.failure(exception);
        }
    }

    private void handleHeartbeatResult(HeartbeatTickResult result) {
        if (result.success()) {
            agentStateStore.recordSuccessfulHeartbeat(result.timestamp(), result.message());
            log.info(result.message());
            return;
        }

        agentStateStore.recordHeartbeatFailure(result.message());
        log.warn(result.message(), result.error());
    }

    private void logHeartbeatResult(HeartbeatTickResult result) {
        if (result.success()) {
            log.debug(result.message());
            return;
        }

        log.warn(result.message(), result.error());
    }

    private void refreshConfigLabels(AgentConfig config) {
        syncConfigurationState(config);
        runOnFxApplicationThread(() -> {
            view.refreshConfig(config, hasStoredApiKey());
            refreshActionButtonState(config);
        });
    }

    private void refreshActionButtonState(AgentConfig config) {
        boolean canRegister = config.hasMasterBaseUrl() && !config.hasWorkerId();
        boolean canUseWorkerApi = config.hasMasterBaseUrl()
                && config.hasWorkerId()
                && hasStoredApiKey();

        boolean heartbeatRunning = runtime.heartbeatScheduler().isRunning();

        view.refreshActionButtonState(
                canRegister,
                canUseWorkerApi,
                heartbeatRunning
        );
    }

    private void syncConfigurationState(AgentConfig config) {
        agentStateStore.syncConfiguration(config, canUseWorkerApi(config));
    }

    private boolean canUseWorkerApi(AgentConfig config) {
        return config.hasMasterBaseUrl()
                && config.hasWorkerId()
                && hasStoredApiKey();
    }

    private void updateHardwareSpec() {
        if (!saveConfigFromFields()) {
            return;
        }

        AgentConfig config = runtime.configService().load();

        try {
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());
        } catch (RuntimeException exception) {
            agentStateStore.setLastError("Cannot update hardware spec: " + exception.getMessage());
            return;
        }

        view.updateHardwareSpecButton().setDisable(true);
        agentStateStore.setLastMessage("Updating hardware spec...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                AgentConfig currentConfig = runtime.configService().load();

                MachineSpec machineSpec = runtime.systemInfoProvider()
                        .collectMachineSpec(currentConfig.sharedRamMb());

                WorkerHardwareUpdateRequest request = WorkerHardwareUpdateRequest.fromMachineSpec(machineSpec);

                runtime.registrationClient().updateHardwareSpec(
                        currentConfig.masterBaseUrl(),
                        currentConfig.workerId(),
                        loadRequiredApiKey(),
                        request
                );

                return null;
            }
        };

        task.setOnSucceeded(event -> {
            agentStateStore.markMasterConnected("Hardware spec updated.");
            view.updateHardwareSpecButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            agentStateStore.setLastError("Hardware spec update failed: " + exception.getMessage());
            view.updateHardwareSpecButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());

            log.warn("Hardware spec update failed", exception);
        });

        runtime.backgroundExecutor().submit(task);
    }

    private void autoStartHeartbeatIfReady(AgentConfig config) {
        if (runtime.heartbeatScheduler().isRunning()) {
            return;
        }

        try {
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());

            agentStateStore.setHeartbeatState(HeartbeatState.STARTING);
            runtime.heartbeatScheduler().start(HEARTBEAT_INTERVAL, result ->
                    logHeartbeatResult(result)
            );

            refreshActionButtonState(config);
            agentStateStore.setHeartbeatState(HeartbeatState.RUNNING);
            agentStateStore.setLastMessage("Heartbeat scheduler started automatically.");
        } catch (RuntimeException exception) {
            refreshActionButtonState(config);
            agentStateStore.setHeartbeatState(HeartbeatState.STOPPED);
            log.info("Heartbeat scheduler was not started automatically: {}", exception.getMessage());
        }
    }

    private void autoStartTaskPollingIfReady(AgentConfig config) {
        if (runtime.taskPollingService().isRunning()) {
            return;
        }

        try {
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());
            runtime.taskPollingService().start(TASK_POLLING_INTERVAL);
            agentStateStore.setLastMessage("Task polling scheduler started automatically.");
        } catch (RuntimeException exception) {
            log.info("Task polling scheduler was not started automatically: {}", exception.getMessage());
        }
    }

    private boolean hasStoredApiKey() {
        return runtime.credentialStore().hasApiKey();
    }

    private String loadRequiredApiKey() {
        return runtime.credentialStore()
                .loadApiKey()
                .orElseThrow(() -> new IllegalStateException("API key is required."));
    }

    private void applyStateToView(AgentStateSnapshot snapshot) {
        Optional<CurrentExecution> currentExecution = runtime.currentExecutionStore().currentExecution();
        Optional<AgentTaskHistoryEntry> latestTaskHistory = latestTaskHistoryEntry();

        runOnFxApplicationThread(() -> view.applyAgentState(
                snapshot,
                currentExecution,
                latestTaskHistory
        ));
    }

    private Optional<AgentTaskHistoryEntry> latestTaskHistoryEntry() {
        try {
            return runtime.taskHistoryStore().findLatest(1).stream().findFirst();
        } catch (RuntimeException exception) {
            log.warn("Failed to read latest task history for dashboard: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static void runOnFxApplicationThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }

        Platform.runLater(runnable);
    }

}
