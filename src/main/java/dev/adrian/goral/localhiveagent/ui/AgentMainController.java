package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.app.AgentRuntime;
import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatTickResult;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationResult;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatRequest;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatResponse;
import dev.adrian.goral.localhiveagent.master.dto.WorkerHardwareUpdateRequest;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import dev.adrian.goral.localhiveagent.validation.AgentConfigValidator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class AgentMainController {

    private static final Logger log = LoggerFactory.getLogger(AgentMainController.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final DateTimeFormatter HEARTBEAT_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AgentRuntime runtime;
    private final AgentMainView view;

    public AgentMainController(AgentRuntime runtime, AgentMainView view) {
        this.runtime = Objects.requireNonNull(runtime);
        this.view = Objects.requireNonNull(view);
    }

    public void initialize() {
        wireViewActions();

        AgentConfig config = runtime.configService().loadOrCreate();
        refreshActionButtonState(config);
        autoStartHeartbeatIfReady(config);
    }

    private void wireViewActions() {
        view.saveConfigButton().setOnAction(event -> saveConfigFromFields());
        view.updateAllocationButton().setOnAction(event -> updateAllocation());
        view.pauseResumeButton().setOnAction(event -> togglePauseMode());
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

            view.setStatus("Config saved.");
            autoStartHeartbeatIfReady(updatedConfig);

            return true;
        } catch (RuntimeException exception) {
            log.warn("Failed to save config", exception);
            view.setStatus("Failed to save config: " + exception.getMessage());
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
        view.setStatus("Registering with Master...");

        Task<AgentRegistrationResult> task = new Task<>() {
            @Override
            protected AgentRegistrationResult call() {
                return runtime.agentRegistrationService().registerCurrentMachine();
            }
        };

        task.setOnSucceeded(event -> {
            AgentRegistrationResult result = task.getValue();

            refreshConfigLabels(result.updatedConfig());

            view.setStatus("Registration completed: " + result.response().message());
            view.setMasterConnectionConnected();
            view.registerButton().setDisable(true);

            log.info("Worker registered successfully. Worker ID: {}", result.updatedConfig().workerId());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            view.setStatus("Registration failed: " + exception.getMessage());
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
        view.setStatus("Sending heartbeat...");

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

            view.setStatus("Heartbeat failed: " + exception.getMessage());
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

            runtime.heartbeatScheduler().start(HEARTBEAT_INTERVAL, result ->
                    Platform.runLater(() -> handleHeartbeatResult(result))
            );

            refreshActionButtonState(config);
            view.setStatus("Heartbeat scheduler started.");
        } catch (RuntimeException exception) {
            runtime.heartbeatScheduler().stop();
            refreshActionButtonState(runtime.configService().load());

            log.warn("Failed to start heartbeat scheduler", exception);
            view.setStatus("Failed to start heartbeat scheduler: " + exception.getMessage());
        }
    }

    private void stopHeartbeat() {
        runtime.heartbeatScheduler().stop();

        refreshActionButtonState(runtime.configService().load());
        view.setStatus("Heartbeat scheduler stopped.");
    }

    private void updateAllocation() {
        if (!saveConfigFromFields()) {
            return;
        }

        AgentConfig config = runtime.configService().load();

        try {
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());
        } catch (RuntimeException exception) {
            view.setStatus("Cannot update allocation: " + exception.getMessage());
            return;
        }

        view.updateAllocationButton().setDisable(true);
        view.setStatus("Updating allocation...");

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
            view.setStatus("Allocation updated: " + runtime.configService().load().sharedRamMb() + " MB");
            view.setMasterConnectionConnected();
            view.updateAllocationButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            view.setStatus("Allocation update failed: " + exception.getMessage());
            view.updateAllocationButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());

            log.warn("Allocation update failed", exception);
        });

        runtime.backgroundExecutor().submit(task);
    }

    private void togglePauseMode() {
        if (!saveConfigFromFields()) {
            return;
        }

        AgentConfig currentConfig = runtime.configService().load();

        try {
            AgentConfigValidator.validateWorkerApiReady(currentConfig, hasStoredApiKey());
        } catch (RuntimeException exception) {
            view.setStatus("Cannot change Gamer Mode: " + exception.getMessage());
            return;
        }

        boolean previousPauseState = currentConfig.pauseEnabled();
        boolean newPauseState = !previousPauseState;

        AgentConfig updatedConfig = runtime.configService().update(config -> config.withPauseEnabled(newPauseState));
        refreshConfigLabels(updatedConfig);

        view.pauseResumeButton().setDisable(true);
        view.setStatus(newPauseState ? "Enabling Gamer Mode..." : "Disabling Gamer Mode...");

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
                view.setStatus(newPauseState
                        ? "Gamer Mode enabled. Worker is paused."
                        : "Gamer Mode disabled. Worker is active.");
            } else {
                AgentConfig rolledBackConfig = runtime.configService()
                        .update(config -> config.withPauseEnabled(previousPauseState));

                refreshConfigLabels(rolledBackConfig);

                view.setStatus("Gamer Mode change failed: " + result.error().getMessage());
                log.warn("Gamer Mode change failed", result.error());
            }

            view.pauseResumeButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            AgentConfig rolledBackConfig = runtime.configService()
                    .update(config -> config.withPauseEnabled(previousPauseState));

            refreshConfigLabels(rolledBackConfig);

            view.setStatus("Gamer Mode change failed: " + exception.getMessage());
            view.pauseResumeButton().setDisable(false);
            refreshActionButtonState(rolledBackConfig);

            log.warn("Gamer Mode change failed", exception);
        });

        runtime.backgroundExecutor().submit(task);
    }

    private HeartbeatTickResult executeHeartbeatOnce() {
        try {
            AgentConfig config = runtime.configService().load();
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());

            HeartbeatRequest request = new HeartbeatRequest(
                    config.pauseEnabled(),
                    config.sharedRamMb()
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
            view.setStatus(result.message());
            view.setMasterConnectionConnected();
            view.setLastHeartbeat("Last successful heartbeat: " + formatTimestamp(result.timestamp()));
            log.info(result.message());
            return;
        }

        view.setStatus(result.message());
        view.setMasterConnectionIssue();
        log.warn(result.message(), result.error());
    }

    private void refreshConfigLabels(AgentConfig config) {
        Platform.runLater(() -> {
            view.refreshConfig(config, hasStoredApiKey());
            refreshActionButtonState(config);
        });
    }

    private void refreshActionButtonState(AgentConfig config) {
        boolean canRegister = config.hasMasterBaseUrl() && !config.hasWorkerId();
        boolean canUseWorkerApi = config.hasMasterBaseUrl()
                && config.hasWorkerId()
                && hasStoredApiKey();

        view.refreshActionButtonState(
                canRegister,
                canUseWorkerApi,
                runtime.heartbeatScheduler().isRunning()
        );
    }

    private void updateHardwareSpec() {
        if (!saveConfigFromFields()) {
            return;
        }

        AgentConfig config = runtime.configService().load();

        try {
            AgentConfigValidator.validateWorkerApiReady(config, hasStoredApiKey());
        } catch (RuntimeException exception) {
            view.setStatus("Cannot update hardware spec: " + exception.getMessage());
            return;
        }

        view.updateHardwareSpecButton().setDisable(true);
        view.setStatus("Updating hardware spec...");

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
            view.setStatus("Hardware spec updated.");
            view.setMasterConnectionConnected();
            view.updateHardwareSpecButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            view.setStatus("Hardware spec update failed: " + exception.getMessage());
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

            runtime.heartbeatScheduler().start(HEARTBEAT_INTERVAL, result ->
                    Platform.runLater(() -> handleHeartbeatResult(result))
            );

            refreshActionButtonState(config);
            view.setStatus("Heartbeat scheduler started automatically.");
        } catch (RuntimeException exception) {
            refreshActionButtonState(config);
            log.info("Heartbeat scheduler was not started automatically: {}", exception.getMessage());
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

    private static String formatTimestamp(Instant timestamp) {
        return HEARTBEAT_TIME_FORMATTER.format(timestamp);
    }

}
