package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.state.AgentStateSnapshot;
import dev.adrian.goral.localhiveagent.state.HeartbeatState;
import dev.adrian.goral.localhiveagent.state.MasterConnectionState;
import dev.adrian.goral.localhiveagent.state.WorkerMode;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AgentMainView {

    private static final DateTimeFormatter HEARTBEAT_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AgentConfig initialConfig;
    private final MachineSpec machineSpec;
    private final Path configPath;
    private final int detectedTotalRamMb;
    private final boolean initialApiKeyConfigured;
    private final String credentialBackendName;
    private final boolean credentialBackendSecure;

    private StatusBadge connectionBadge;
    private StatusBadge workerStatusBadge;
    private StatusBadge heartbeatBadge;
    private StatusBadge lastHeartbeatBadge;

    private ResourceOverviewPane resourceOverviewPane;
    private SharedRamPane sharedRamPane;
    private GamerModePane gamerModePane;
    private AgentStatePane agentStatePane;
    private MaintenanceActionsPane maintenanceActionsPane;

    public AgentMainView(
            AgentConfig initialConfig,
            MachineSpec machineSpec,
            Path configPath,
            boolean initialApiKeyConfigured,
            String credentialBackendName,
            boolean credentialBackendSecure
    ) {
        this.initialConfig = initialConfig;
        this.machineSpec = machineSpec;
        this.configPath = configPath;
        this.detectedTotalRamMb = machineSpec.totalRamMb();
        this.initialApiKeyConfigured = initialApiKeyConfigured;
        this.credentialBackendName = credentialBackendName;
        this.credentialBackendSecure = credentialBackendSecure;
    }

    public Parent createRoot() {
        resourceOverviewPane = new ResourceOverviewPane(initialConfig, machineSpec);
        sharedRamPane = new SharedRamPane(initialConfig, detectedTotalRamMb);
        gamerModePane = new GamerModePane(initialConfig);
        agentStatePane = new AgentStatePane(
                initialConfig,
                configPath,
                initialApiKeyConfigured,
                credentialBackendName,
                credentialBackendSecure
        );
        maintenanceActionsPane = new MaintenanceActionsPane();

        VBox root = new VBox();
        root.getStyleClass().add("dashboard-root");

        ScrollPane contentScroll = new ScrollPane(createDashboardContent());
        contentScroll.getStyleClass().add("dashboard-scroll");
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        root.getChildren().addAll(createHeader(), contentScroll);
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        refreshConfig(initialConfig, initialApiKeyConfigured);

        return root;
    }

    public int detectedTotalRamMb() {
        return detectedTotalRamMb;
    }

    public String masterBaseUrlInput() {
        return agentStatePane.masterBaseUrlInput();
    }

    public String sharedRamMbInput() {
        return sharedRamPane.sharedRamMbInput();
    }

    public String apiKeyInput() {
        return agentStatePane.apiKeyInput();
    }

    public void clearApiKeyInput() {
        agentStatePane.clearApiKeyInput();
    }

    public void refreshConfig(AgentConfig config, boolean apiKeyConfigured) {
        agentStatePane.refreshConfig(config, apiKeyConfigured);
        sharedRamPane.refreshSharedRam(config.sharedRamMb());
        resourceOverviewPane.setSharedRamMb(config.sharedRamMb());
    }

    public void refreshActionButtonState(boolean canRegister, boolean canUseWorkerApi, boolean heartbeatRunning) {
        registerButton().setDisable(!canRegister);
        updateAllocationButton().setDisable(!canUseWorkerApi);
        pauseResumeButton().setDisable(!canUseWorkerApi);
        heartbeatNowButton().setDisable(!canUseWorkerApi);
        startHeartbeatButton().setDisable(!canUseWorkerApi || heartbeatRunning);
        stopHeartbeatButton().setDisable(!heartbeatRunning);
        updateHardwareSpecButton().setDisable(!canUseWorkerApi);

    }

    public void applyAgentState(AgentStateSnapshot snapshot) {
        applyMasterConnectionState(snapshot.masterConnectionState());
        applyWorkerMode(snapshot);
        applyHeartbeatState(snapshot.heartbeatState());
        applyLastHeartbeat(snapshot);
        agentStatePane.refreshTaskState(
                snapshot.taskPollingEnabled(),
                snapshot.currentExecutionSummary(),
                snapshot.taskHistoryCount(),
                snapshot.latestTaskHistorySummary()
        );
        applyLastMessage(snapshot);
    }

    public Button saveConfigButton() {
        return agentStatePane.saveConfigButton();
    }

    public Button registerButton() {
        return agentStatePane.registerButton();
    }

    public Button updateAllocationButton() {
        return sharedRamPane.updateAllocationButton();
    }

    public Button pauseResumeButton() {
        return gamerModePane.pauseResumeButton();
    }

    public Button heartbeatNowButton() {
        return maintenanceActionsPane.heartbeatNowButton();
    }

    public Button startHeartbeatButton() {
        return maintenanceActionsPane.startHeartbeatButton();
    }

    public Button stopHeartbeatButton() {
        return maintenanceActionsPane.stopHeartbeatButton();
    }

    public Button updateHardwareSpecButton() {
        return maintenanceActionsPane.updateHardwareSpecButton();
    }

    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dashboard-header");

        VBox titleBox = new VBox(4);
        Label title = new Label("LocalHive Agent");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Local worker dashboard");
        subtitle.getStyleClass().add("app-subtitle");

        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        FlowPane statusPane = new FlowPane();
        statusPane.setHgap(8);
        statusPane.setVgap(8);
        statusPane.setAlignment(Pos.CENTER_RIGHT);
        statusPane.getStyleClass().add("header-status-pane");

        connectionBadge = new StatusBadge(Feather.WIFI_OFF, "Master: not configured", StatusBadge.WARNING);
        workerStatusBadge = new StatusBadge(Feather.ALERT_CIRCLE, "Mode: unregistered", StatusBadge.WARNING);
        heartbeatBadge = new StatusBadge(Feather.ACTIVITY, "Heartbeat: stopped", StatusBadge.NEUTRAL);
        lastHeartbeatBadge = new StatusBadge(Feather.CLOCK, "Last successful heartbeat: never", StatusBadge.NEUTRAL);

        statusPane.getChildren().addAll(
                connectionBadge,
                workerStatusBadge,
                heartbeatBadge,
                lastHeartbeatBadge
        );

        header.getChildren().addAll(titleBox, spacer, statusPane);

        return header;
    }

    private VBox createDashboardContent() {
        VBox content = new VBox(12);
        content.getStyleClass().add("dashboard-content");

        DashboardSection resourcesSection = new DashboardSection(
                Feather.HARD_DRIVE,
                "Resources",
                resourceOverviewPane
        );

        GridPane dashboardGrid = new GridPane();
        dashboardGrid.setHgap(12);
        dashboardGrid.setVgap(12);

        ColumnConstraints primaryColumn = new ColumnConstraints();
        primaryColumn.setPercentWidth(62);
        primaryColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints secondaryColumn = new ColumnConstraints();
        secondaryColumn.setPercentWidth(38);
        secondaryColumn.setHgrow(Priority.ALWAYS);

        dashboardGrid.getColumnConstraints().addAll(primaryColumn, secondaryColumn);

        dashboardGrid.add(new DashboardSection(Feather.SLIDERS, "Shared RAM", sharedRamPane), 0, 0);
        dashboardGrid.add(new DashboardSection(Feather.PLAY_CIRCLE, "Gamer Mode", gamerModePane), 1, 0);
        dashboardGrid.add(new DashboardSection(Feather.INFO, "Agent State", agentStatePane), 0, 1);
        dashboardGrid.add(new DashboardSection(Feather.TOOL, "Maintenance", maintenanceActionsPane), 1, 1);

        content.getChildren().addAll(resourcesSection, dashboardGrid);

        return content;
    }

    private void applyMasterConnectionState(MasterConnectionState state) {
        switch (state) {
            case NOT_CONFIGURED -> connectionBadge.setStatus(
                    Feather.WIFI_OFF,
                    "Master: not configured",
                    StatusBadge.WARNING
            );
            case UNKNOWN -> connectionBadge.setStatus(
                    Feather.WIFI,
                    "Master: configured",
                    StatusBadge.NEUTRAL
            );
            case CONNECTED -> connectionBadge.setStatus(
                    Feather.WIFI,
                    "Master: connected",
                    StatusBadge.SUCCESS
            );
            case ATTENTION_REQUIRED -> connectionBadge.setStatus(
                    Feather.WIFI_OFF,
                    "Master: attention needed",
                    StatusBadge.DANGER
            );
        }
    }

    private void applyWorkerMode(AgentStateSnapshot snapshot) {
        gamerModePane.refresh(snapshot.workerMode() == WorkerMode.PAUSED);

        if (!snapshot.workerRegistered()) {
            workerStatusBadge.setStatus(
                    Feather.ALERT_CIRCLE,
                    "Mode: unregistered",
                    StatusBadge.WARNING
            );
            return;
        }

        if (snapshot.workerMode() == WorkerMode.PAUSED) {
            workerStatusBadge.setStatus(Feather.PAUSE_CIRCLE, "Mode: paused", StatusBadge.WARNING);
            return;
        }

        workerStatusBadge.setStatus(Feather.CHECK_CIRCLE, "Mode: active", StatusBadge.SUCCESS);
    }

    private void applyHeartbeatState(HeartbeatState state) {
        switch (state) {
            case STOPPED -> heartbeatBadge.setStatus(Feather.ACTIVITY, "Heartbeat: stopped", StatusBadge.NEUTRAL);
            case STARTING -> heartbeatBadge.setStatus(Feather.ACTIVITY, "Heartbeat: starting", StatusBadge.NEUTRAL);
            case RUNNING -> heartbeatBadge.setStatus(Feather.ACTIVITY, "Heartbeat: running", StatusBadge.SUCCESS);
            case FAILED -> heartbeatBadge.setStatus(Feather.ACTIVITY, "Heartbeat: failed", StatusBadge.DANGER);
        }
    }

    private void applyLastHeartbeat(AgentStateSnapshot snapshot) {
        if (snapshot.lastSuccessfulHeartbeat() == null) {
            lastHeartbeatBadge.setStatus(
                    Feather.CLOCK,
                    "Last successful heartbeat: never",
                    StatusBadge.NEUTRAL
            );
            return;
        }

        lastHeartbeatBadge.setStatus(
                Feather.CLOCK,
                "Last successful heartbeat: " + HEARTBEAT_TIME_FORMATTER.format(snapshot.lastSuccessfulHeartbeat()),
                StatusBadge.SUCCESS
        );
    }

    private void applyLastMessage(AgentStateSnapshot snapshot) {
        boolean hasError = !snapshot.lastError().isBlank();
        String displayMessage = hasError ? snapshot.lastError() : snapshot.lastMessage();
        agentStatePane.setStatus(displayMessage, hasError);
    }
}
