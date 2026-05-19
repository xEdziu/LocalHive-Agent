package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatScheduler;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatTickResult;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationResult;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationService;
import dev.adrian.goral.localhiveagent.master.RegistrationClient;
import dev.adrian.goral.localhiveagent.master.dto.HeartbeatResponse;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import dev.adrian.goral.localhiveagent.system.OshiSystemInfoProvider;
import dev.adrian.goral.localhiveagent.system.SystemInfoProvider;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalHiveAgentApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(LocalHiveAgentApplication.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private ConfigService configService;
    private SystemInfoProvider systemInfoProvider;
    private RegistrationClient registrationClient;
    private AgentRegistrationService agentRegistrationService;
    private HeartbeatScheduler heartbeatScheduler;
    private ExecutorService backgroundExecutor;

    private Label workerIdLabel;
    private Label apiKeyLabel;
    private Label statusLabel;
    private Label lastHeartbeatLabel;

    private TextField masterBaseUrlField;
    private TextField sharedRamMbField;
    private PasswordField apiKeyField;

    private Button registerButton;
    private Button startHeartbeatButton;
    private Button stopHeartbeatButton;
    private Button heartbeatNowButton;

    @Override
    public void init() {
        Path configPath = Path.of(System.getProperty("user.home"), ".localhive-agent", "config.json");

        this.configService = new ConfigService(configPath);
        this.systemInfoProvider = new OshiSystemInfoProvider();
        this.registrationClient = new RegistrationClient();
        this.agentRegistrationService = new AgentRegistrationService(
                configService,
                systemInfoProvider,
                registrationClient
        );
        this.heartbeatScheduler = new HeartbeatScheduler(configService, registrationClient);
        this.backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "localhive-agent-background");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start(Stage stage) {
        AgentConfig config = configService.loadOrCreate();
        MachineSpec machineSpec = systemInfoProvider.collectMachineSpec(config.sharedRamMb());

        log.info("LocalHive Agent started");
        log.info("Config path: {}", configService.configPath());
        log.info("Worker registered: {}", config.hasWorkerId());
        log.info("API key configured: {}", config.hasApiKey());
        log.info("Detected machine spec: {}", machineSpec);

        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24;");

        root.getChildren().addAll(
                new Label("LocalHive Agent"),
                createConfigSection(config),
                createMachineSpecSection(machineSpec),
                createActionSection(config)
        );

        Scene scene = new Scene(root, 820, 620);

        stage.setTitle("LocalHive Agent");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.close();
        }

        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
    }

    private GridPane createConfigSection(AgentConfig config) {
        GridPane grid = createGrid();

        masterBaseUrlField = new TextField(config.masterBaseUrl());
        masterBaseUrlField.setPromptText("http://localhost:8080");

        sharedRamMbField = new TextField(String.valueOf(config.sharedRamMb()));
        sharedRamMbField.setPromptText("8192");

        apiKeyField = new PasswordField();
        apiKeyField.setPromptText(config.hasApiKey() ? "API key is configured" : "Paste API key after approval");

        workerIdLabel = new Label(config.hasWorkerId() ? config.workerId().toString() : "not registered");
        apiKeyLabel = new Label(config.hasApiKey() ? "configured" : "missing");

        grid.addRow(0, new Label("Config:"), new Label(configService.configPath().toString()));
        grid.addRow(1, new Label("Master URL:"), masterBaseUrlField);
        grid.addRow(2, new Label("Worker ID:"), workerIdLabel);
        grid.addRow(3, new Label("API Key status:"), apiKeyLabel);
        grid.addRow(4, new Label("New API Key:"), apiKeyField);
        grid.addRow(5, new Label("Shared RAM MB:"), sharedRamMbField);
        grid.addRow(6, new Label("Paused:"), new Label(String.valueOf(config.pauseEnabled())));

        return grid;
    }

    private GridPane createMachineSpecSection(MachineSpec machineSpec) {
        GridPane grid = createGrid();

        grid.addRow(0, new Label("Hostname:"), new Label(machineSpec.hostname()));
        grid.addRow(1, new Label("IP address:"), new Label(machineSpec.ipAddress()));
        grid.addRow(2, new Label("OS:"), new Label(machineSpec.osType()));
        grid.addRow(3, new Label("Total RAM:"), new Label(machineSpec.totalRamMb() + " MB"));
        grid.addRow(4, new Label("CPU cores:"), new Label(String.valueOf(machineSpec.cpuCores())));
        grid.addRow(5, new Label("GPU:"), new Label(machineSpec.gpuName().isBlank() ? "not detected" : machineSpec.gpuName()));

        return grid;
    }

    private VBox createActionSection(AgentConfig config) {
        Button saveConfigButton = new Button("Save Config");
        saveConfigButton.setOnAction(event -> saveConfigFromFields());

        registerButton = new Button("Register with Master");
        registerButton.setDisable(config.hasWorkerId());
        registerButton.setOnAction(event -> registerWithMaster());

        heartbeatNowButton = new Button("Send Heartbeat Now");
        heartbeatNowButton.setOnAction(event -> sendHeartbeatNow(heartbeatNowButton));

        startHeartbeatButton = new Button("Start Heartbeat");
        startHeartbeatButton.setOnAction(event -> startHeartbeat());

        stopHeartbeatButton = new Button("Stop Heartbeat");
        stopHeartbeatButton.setDisable(true);
        stopHeartbeatButton.setOnAction(event -> stopHeartbeat());

        statusLabel = new Label("Ready.");
        lastHeartbeatLabel = new Label("Last heartbeat: never");

        VBox box = new VBox(10);
        box.getChildren().addAll(
                saveConfigButton,
                registerButton,
                heartbeatNowButton,
                startHeartbeatButton,
                stopHeartbeatButton,
                statusLabel,
                lastHeartbeatLabel
        );

        refreshActionButtonState(config);

        return box;
    }

    private boolean saveConfigFromFields() {
        try {
            AgentConfig updatedConfig = saveConfigFromFieldsInternal();

            refreshConfigLabels(updatedConfig);
            statusLabel.setText("Config saved.");

            if (!apiKeyField.getText().isBlank()) {
                apiKeyField.clear();
            }

            return true;
        } catch (RuntimeException exception) {
            log.warn("Failed to save config", exception);
            statusLabel.setText("Failed to save config: " + exception.getMessage());
            return false;
        }
    }

    private AgentConfig saveConfigFromFieldsInternal() {
        String masterBaseUrl = masterBaseUrlField.getText();
        String apiKey = apiKeyField.getText();
        int sharedRamMb = parseSharedRamMb(sharedRamMbField.getText());

        return configService.update(config -> {
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

        registerButton.setDisable(true);
        statusLabel.setText("Registering with Master...");

        Task<AgentRegistrationResult> task = new Task<>() {
            @Override
            protected AgentRegistrationResult call() {
                return agentRegistrationService.registerCurrentMachine();
            }
        };

        task.setOnSucceeded(event -> {
            AgentRegistrationResult result = task.getValue();

            refreshConfigLabels(result.updatedConfig());

            statusLabel.setText("Registration completed: " + result.response().message());
            registerButton.setDisable(true);

            log.info("Worker registered successfully. Worker ID: {}", result.updatedConfig().workerId());
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            statusLabel.setText("Registration failed: " + exception.getMessage());
            registerButton.setDisable(false);

            log.warn("Worker registration failed", exception);
        });

        backgroundExecutor.submit(task);
    }

    private void sendHeartbeatNow(Button heartbeatNowButton) {
        if (!saveConfigFromFields()) {
            return;
        }

        heartbeatNowButton.setDisable(true);
        statusLabel.setText("Sending heartbeat...");

        Task<HeartbeatTickResult> task = new Task<>() {
            @Override
            protected HeartbeatTickResult call() {
                try {
                    AgentConfig config = configService.load();
                    validateConfigBeforeHeartbeat(config);

                    HeartbeatResponse response = registrationClient.sendHeartbeat(
                            config.masterBaseUrl(),
                            config.workerId(),
                            config.apiKey()
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
            heartbeatNowButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();

            statusLabel.setText("Heartbeat failed: " + exception.getMessage());
            heartbeatNowButton.setDisable(false);

            log.warn("Heartbeat failed", exception);
        });

        backgroundExecutor.submit(task);
    }

    private void startHeartbeat() {
        if (!saveConfigFromFields()) {
            return;
        }

        try {
            AgentConfig config = configService.load();
            validateConfigBeforeHeartbeat(config);

            heartbeatScheduler.start(HEARTBEAT_INTERVAL, result ->
                    Platform.runLater(() -> handleHeartbeatResult(result))
            );

            startHeartbeatButton.setDisable(true);
            stopHeartbeatButton.setDisable(false);
            statusLabel.setText("Heartbeat scheduler started.");
        } catch (RuntimeException exception) {
            heartbeatScheduler.stop();
            refreshActionButtonState(configService.load());

            log.warn("Failed to start heartbeat scheduler", exception);
            statusLabel.setText("Failed to start heartbeat scheduler: " + exception.getMessage());
        }
    }

    private void stopHeartbeat() {
        heartbeatScheduler.stop();

        startHeartbeatButton.setDisable(false);
        stopHeartbeatButton.setDisable(true);
        statusLabel.setText("Heartbeat scheduler stopped.");
    }

    private void handleHeartbeatResult(HeartbeatTickResult result) {
        if (result.success()) {
            statusLabel.setText(result.message());
            lastHeartbeatLabel.setText("Last heartbeat: " + result.timestamp());
            log.info(result.message());
            return;
        }

        statusLabel.setText(result.message());
        lastHeartbeatLabel.setText("Last heartbeat failed: " + result.timestamp());
        log.warn(result.message(), result.error());
    }

    private void refreshConfigLabels(AgentConfig config) {
        Platform.runLater(() -> {
            workerIdLabel.setText(config.hasWorkerId() ? config.workerId().toString() : "not registered");
            apiKeyLabel.setText(config.hasApiKey() ? "configured" : "missing");
            refreshActionButtonState(config);
        });
    }

    private void refreshActionButtonState(AgentConfig config) {
        boolean canRegister = config.hasMasterBaseUrl() && !config.hasWorkerId();
        boolean canUseHeartbeat = config.hasMasterBaseUrl()
                && config.hasWorkerId()
                && config.hasApiKey();

        if (registerButton != null) {
            registerButton.setDisable(!canRegister);
        }

        if (startHeartbeatButton != null) {
            startHeartbeatButton.setDisable(!canUseHeartbeat || heartbeatScheduler.isRunning());
        }

        if (stopHeartbeatButton != null) {
            stopHeartbeatButton.setDisable(!heartbeatScheduler.isRunning());
        }

        if (heartbeatNowButton != null) {
            heartbeatNowButton.setDisable(!canUseHeartbeat);
        }
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

    private static GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        return grid;
    }

    private static int parseSharedRamMb(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            int sharedRamMb = Integer.parseInt(value.trim());

            if (sharedRamMb < 0) {
                throw new IllegalArgumentException("Shared RAM cannot be negative.");
            }

            return sharedRamMb;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Shared RAM must be a valid integer.", exception);
        }
    }
}