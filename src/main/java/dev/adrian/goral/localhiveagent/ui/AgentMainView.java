package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

public class AgentMainView {

    private final AgentConfig initialConfig;
    private final MachineSpec machineSpec;
    private final Path configPath;
    private final int detectedTotalRamMb;

    private Label workerIdLabel;
    private Label apiKeyLabel;
    private Label statusLabel;
    private Label lastHeartbeatLabel;
    private Label pauseStatusLabel;

    private TextField masterBaseUrlField;
    private TextField sharedRamMbField;
    private PasswordField apiKeyField;
    private Slider sharedRamSlider;

    private Button saveConfigButton;
    private Button registerButton;
    private Button updateAllocationButton;
    private Button pauseResumeButton;
    private Button heartbeatNowButton;
    private Button startHeartbeatButton;
    private Button stopHeartbeatButton;
    private Button updateHardwareSpecButton;

    public AgentMainView(AgentConfig initialConfig, MachineSpec machineSpec, Path configPath) {
        this.initialConfig = initialConfig;
        this.machineSpec = machineSpec;
        this.configPath = configPath;
        this.detectedTotalRamMb = machineSpec.totalRamMb();
    }

    public VBox createRoot() {
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24;");

        root.getChildren().addAll(
                new Label("LocalHive Agent"),
                createConfigSection(),
                createMachineSpecSection(),
                createActionSection()
        );

        return root;
    }

    public int detectedTotalRamMb() {
        return detectedTotalRamMb;
    }

    public String masterBaseUrlInput() {
        return masterBaseUrlField.getText();
    }

    public String sharedRamMbInput() {
        return sharedRamMbField.getText();
    }

    public String apiKeyInput() {
        return apiKeyField.getText();
    }

    public void clearApiKeyInput() {
        apiKeyField.clear();
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setLastHeartbeat(String value) {
        lastHeartbeatLabel.setText(value);
    }

    public void refreshConfig(AgentConfig config) {
        workerIdLabel.setText(config.hasWorkerId() ? config.workerId().toString() : "not registered");
        apiKeyLabel.setText(config.hasApiKey() ? "configured" : "missing");
        pauseStatusLabel.setText(String.valueOf(config.pauseEnabled()));
        pauseResumeButton.setText(getPauseResumeButtonText(config.pauseEnabled()));
    }

    public void refreshActionButtonState(boolean canRegister, boolean canUseWorkerApi, boolean heartbeatRunning) {
        registerButton.setDisable(!canRegister);
        updateAllocationButton.setDisable(!canUseWorkerApi);
        pauseResumeButton.setDisable(!canUseWorkerApi);
        heartbeatNowButton.setDisable(!canUseWorkerApi);
        startHeartbeatButton.setDisable(!canUseWorkerApi || heartbeatRunning);
        stopHeartbeatButton.setDisable(!heartbeatRunning);
        updateHardwareSpecButton.setDisable(!canUseWorkerApi);
    }

    public Button saveConfigButton() {
        return saveConfigButton;
    }

    public Button registerButton() {
        return registerButton;
    }

    public Button updateAllocationButton() {
        return updateAllocationButton;
    }

    public Button pauseResumeButton() {
        return pauseResumeButton;
    }

    public Button heartbeatNowButton() {
        return heartbeatNowButton;
    }

    public Button startHeartbeatButton() {
        return startHeartbeatButton;
    }

    public Button stopHeartbeatButton() {
        return stopHeartbeatButton;
    }

    public Button updateHardwareSpecButton() {
        return updateHardwareSpecButton;
    }

    private GridPane createConfigSection() {
        GridPane grid = createGrid();

        masterBaseUrlField = new TextField(initialConfig.masterBaseUrl());
        masterBaseUrlField.setPromptText("http://localhost:8080");

        sharedRamMbField = new TextField(String.valueOf(initialConfig.sharedRamMb()));
        sharedRamMbField.setPromptText("8192");

        sharedRamSlider = new Slider(0, Math.max(1024, detectedTotalRamMb), initialConfig.sharedRamMb());
        sharedRamSlider.setShowTickLabels(true);
        sharedRamSlider.setShowTickMarks(true);
        sharedRamSlider.setMajorTickUnit(8192);
        sharedRamSlider.setBlockIncrement(512);

        sharedRamSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            int roundedValue = roundToStep(newValue.intValue(), 512);
            sharedRamMbField.setText(String.valueOf(roundedValue));
        });

        sharedRamMbField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isBlank()) {
                return;
            }

            try {
                int parsedValue = Integer.parseInt(newValue.trim());

                if (parsedValue >= 0 && parsedValue <= detectedTotalRamMb) {
                    sharedRamSlider.setValue(parsedValue);
                }
            } catch (NumberFormatException ignored) {
                // Invalid input is handled during config saving.
            }
        });

        apiKeyField = new PasswordField();
        apiKeyField.setPromptText(initialConfig.hasApiKey()
                ? "API key is configured"
                : "Paste API key after approval");

        workerIdLabel = new Label(initialConfig.hasWorkerId()
                ? initialConfig.workerId().toString()
                : "not registered");

        apiKeyLabel = new Label(initialConfig.hasApiKey() ? "configured" : "missing");
        pauseStatusLabel = new Label(String.valueOf(initialConfig.pauseEnabled()));

        grid.addRow(0, new Label("Config:"), new Label(configPath.toString()));
        grid.addRow(1, new Label("Master URL:"), masterBaseUrlField);
        grid.addRow(2, new Label("Worker ID:"), workerIdLabel);
        grid.addRow(3, new Label("API Key status:"), apiKeyLabel);
        grid.addRow(4, new Label("New API Key:"), apiKeyField);
        grid.addRow(5, new Label("Shared RAM MB:"), sharedRamMbField);
        grid.addRow(6, new Label("Shared RAM slider:"), sharedRamSlider);
        grid.addRow(7, new Label("Paused:"), pauseStatusLabel);

        return grid;
    }

    private GridPane createMachineSpecSection() {
        GridPane grid = createGrid();

        grid.addRow(0, new Label("Hostname:"), new Label(machineSpec.hostname()));
        grid.addRow(1, new Label("IP address:"), new Label(machineSpec.ipAddress()));
        grid.addRow(2, new Label("OS:"), new Label(machineSpec.osType()));
        grid.addRow(3, new Label("Total RAM:"), new Label(machineSpec.totalRamMb() + " MB"));
        grid.addRow(4, new Label("CPU cores:"), new Label(String.valueOf(machineSpec.cpuCores())));
        grid.addRow(5, new Label("GPU:"), new Label(machineSpec.gpuName().isBlank()
                ? "not detected"
                : machineSpec.gpuName()));

        return grid;
    }

    private VBox createActionSection() {
        saveConfigButton = new Button("Save Config");
        updateAllocationButton = new Button("Update Allocation");
        pauseResumeButton = new Button(getPauseResumeButtonText(initialConfig.pauseEnabled()));
        registerButton = new Button("Register with Master");
        heartbeatNowButton = new Button("Send Heartbeat Now");
        startHeartbeatButton = new Button("Start Heartbeat");
        stopHeartbeatButton = new Button("Stop Heartbeat");
        updateHardwareSpecButton = new Button("Update Hardware Spec");

        statusLabel = new Label("Ready.");
        lastHeartbeatLabel = new Label("Last heartbeat: never");

        VBox box = new VBox(10);
        box.getChildren().addAll(
                saveConfigButton,
                updateAllocationButton,
                updateHardwareSpecButton,
                pauseResumeButton,
                registerButton,
                heartbeatNowButton,
                startHeartbeatButton,
                stopHeartbeatButton,
                statusLabel,
                lastHeartbeatLabel
        );

        return box;
    }

    private static GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        return grid;
    }

    private static String getPauseResumeButtonText(boolean pauseEnabled) {
        return pauseEnabled ? "Resume" : "Pause";
    }

    private static int roundToStep(int value, int step) {
        if (step <= 0) {
            return value;
        }

        return Math.round((float) value / step) * step;
    }
}