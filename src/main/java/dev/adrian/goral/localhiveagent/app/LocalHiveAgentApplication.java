package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatTickResult;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationResult;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatRequest;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatResponse;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import dev.adrian.goral.localhiveagent.ui.AgentMainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class LocalHiveAgentApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(LocalHiveAgentApplication.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private AgentRuntime runtime;
    private AgentMainView view;


    @Override
    public void init() {
        this.runtime = AgentRuntime.createDefault();
    }

    @Override
    public void start(Stage stage) {
        AgentConfig config = runtime.configService().loadOrCreate();
        MachineSpec machineSpec = runtime.systemInfoProvider().collectMachineSpec(config.sharedRamMb());

        log.info("LocalHive Agent started");
        log.info("Config path: {}", runtime.configService().configPath());
        log.info("Worker registered: {}", config.hasWorkerId());
        log.info("API key configured: {}", config.hasApiKey());
        log.info("Detected machine spec: {}", machineSpec);

        this.view = new AgentMainView(
                config,
                machineSpec,
                runtime.configService().configPath()
        );

        Parent root = view.createRoot();

        wireViewActions();
        refreshActionButtonState(config);

        Scene scene = new Scene(root, 820, 620);

        stage.setTitle("LocalHive Agent");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (runtime != null) {
            runtime.close();
        }
    }

    private void wireViewActions() {
        view.saveConfigButton().setOnAction(event -> saveConfigFromFields());
        view.updateAllocationButton().setOnAction(event -> updateAllocation());
        view.pauseResumeButton().setOnAction(event -> togglePauseMode());
        view.registerButton().setOnAction(event -> registerWithMaster());
        view.heartbeatNowButton().setOnAction(event -> sendHeartbeatNow());
        view.startHeartbeatButton().setOnAction(event -> startHeartbeat());
        view.stopHeartbeatButton().setOnAction(event -> stopHeartbeat());
    }

    private boolean saveConfigFromFields() {
        try {
            AgentConfig updatedConfig = saveConfigFromFieldsInternal();

            refreshConfigLabels(updatedConfig);
            view.setStatus("Config saved.");

            if (!view.apiKeyInput().isBlank()) {
                view.clearApiKeyInput();
            }

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
        int sharedRamMb = parseSharedRamMb(view.sharedRamMbInput());

        return runtime.configService().update(config -> {
            AgentConfig updatedConfig = config
                    .withMasterBaseUrl(masterBaseUrl)
                    .withSharedRamMb(sharedRamMb);

            if (apiKey != null && !apiKey.isBlank()) {
                updatedConfig = updatedConfig.withApiKey(apiKey);
            }

            return updatedConfig;
        });
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
            view.registerButton().setDisable(true);

            log.info("Worker registered successfully. Worker ID: {}", result.updatedConfig().workerId());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            view.setStatus("Registration failed: " + exception.getMessage());
            view.registerButton().setDisable(false);

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
                try {
                    AgentConfig config = runtime.configService().load();
                    validateConfigBeforeHeartbeat(config);

                    HeartbeatRequest request = new HeartbeatRequest(
                            config.pauseEnabled(),
                            config.sharedRamMb()
                    );

                    HeartbeatResponse response = runtime.registrationClient().sendHeartbeat(
                            config.masterBaseUrl(),
                            config.workerId(),
                            config.apiKey(),
                            request
                    );

                    return HeartbeatTickResult.success(response.status());
                } catch (RuntimeException exception) {
                    return HeartbeatTickResult.failure(exception);
                }
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
            validateConfigBeforeHeartbeat(config);

            runtime.heartbeatScheduler().start(HEARTBEAT_INTERVAL, result ->
                    Platform.runLater(() -> handleHeartbeatResult(result))
            );

            view.startHeartbeatButton().setDisable(true);
            view.stopHeartbeatButton().setDisable(false);
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

        view.startHeartbeatButton().setDisable(false);
        view.stopHeartbeatButton().setDisable(true);
        view.setStatus("Heartbeat scheduler stopped.");
    }

    private void handleHeartbeatResult(HeartbeatTickResult result) {
        if (result.success()) {
            view.setStatus(result.message());
            view.setLastHeartbeat("Last heartbeat: " + result.timestamp());
            log.info(result.message());
            return;
        }

        view.setStatus(result.message());
        view.setLastHeartbeat("Last heartbeat failed: " + result.timestamp());
        log.warn(result.message(), result.error());
    }

    private void refreshConfigLabels(AgentConfig config) {
        Platform.runLater(() -> {
            view.refreshConfig(config);
            refreshActionButtonState(config);
        });
    }

    private void refreshActionButtonState(AgentConfig config) {
        boolean canRegister = config.hasMasterBaseUrl() && !config.hasWorkerId();
        boolean canUseWorkerApi = config.hasMasterBaseUrl()
                && config.hasWorkerId()
                && config.hasApiKey();

        view.refreshActionButtonState(
                canRegister,
                canUseWorkerApi,
                runtime.heartbeatScheduler().isRunning()
        );
    }

    private static void validateConfigBeforeHeartbeat(AgentConfig config) {
        if (!config.hasMasterBaseUrl()) {
            throw new IllegalStateException("Master base URL is required before heartbeat.");
        }

        if (!config.hasWorkerId()) {
            throw new IllegalStateException("Worker ID is required before heartbeat.");
        }

        if (!config.hasApiKey()) {
            throw new IllegalStateException("API key is required before heartbeat.");
        }
    }

    private int parseSharedRamMb(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            int sharedRamMb = Integer.parseInt(value.trim());

            if (sharedRamMb < 0) {
                throw new IllegalArgumentException("Shared RAM cannot be negative.");
            }

            if (sharedRamMb > view.detectedTotalRamMb()) {
                throw new IllegalArgumentException("Shared RAM cannot be greater than total RAM.");
            }

            return sharedRamMb;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Shared RAM must be a valid integer.", exception);
        }
    }

    private void updateAllocation() {
        if (!saveConfigFromFields()) {
            return;
        }

        AgentConfig config = runtime.configService().load();

        try {
            validateConfigBeforeHeartbeat(config);
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
                        currentConfig.apiKey(),
                        currentConfig.sharedRamMb()
                );

                return null;
            }
        };

        task.setOnSucceeded(event -> {
            view.setStatus("Allocation updated: " + runtime.configService().load().sharedRamMb() + " MB");
            view.updateAllocationButton().setDisable(false);
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            view.setStatus("Allocation update failed: " + exception.getMessage());
            view.updateAllocationButton().setDisable(false);

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
            validateConfigBeforeHeartbeat(currentConfig);
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
                try {
                    AgentConfig config = runtime.configService().load();
                    validateConfigBeforeHeartbeat(config);

                    HeartbeatRequest request = new HeartbeatRequest(
                            config.pauseEnabled(),
                            config.sharedRamMb()
                    );

                    HeartbeatResponse response = runtime.registrationClient().sendHeartbeat(
                            config.masterBaseUrl(),
                            config.workerId(),
                            config.apiKey(),
                            request
                    );

                    return HeartbeatTickResult.success(response.status());
                } catch (RuntimeException exception) {
                    return HeartbeatTickResult.failure(exception);
                }
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
                AgentConfig rolledBackConfig = runtime.configService().update(config -> config.withPauseEnabled(previousPauseState));
                refreshConfigLabels(rolledBackConfig);

                view.setStatus("Gamer Mode change failed: " + result.error().getMessage());
                log.warn("Gamer Mode change failed", result.error());
            }

            view.pauseResumeButton().setDisable(false);
            refreshActionButtonState(runtime.configService().load());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            AgentConfig rolledBackConfig = runtime.configService().update(config -> config.withPauseEnabled(previousPauseState));
            refreshConfigLabels(rolledBackConfig);

            view.setStatus("Gamer Mode change failed: " + exception.getMessage());
            view.pauseResumeButton().setDisable(false);
            refreshActionButtonState(rolledBackConfig);

            log.warn("Gamer Mode change failed", exception);
        });

        runtime.backgroundExecutor().submit(task);
    }
}