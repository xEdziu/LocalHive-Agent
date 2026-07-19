package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.state.AgentStateSnapshot;
import dev.adrian.goral.localhiveagent.state.HeartbeatState;
import dev.adrian.goral.localhiveagent.state.MasterConnectionState;
import dev.adrian.goral.localhiveagent.state.WorkerMode;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

class ConnectionStatusPane extends VBox {

    private static final List<String> STATE_CLASSES = List.of(
            "state-good",
            "state-warning",
            "state-danger"
    );
    private static final DateTimeFormatter HEARTBEAT_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Label masterConnectionLabel;
    private final Label workerModeLabel;
    private final Label workerApiReadyLabel;
    private final Label apiKeyConfiguredLabel;
    private final Label heartbeatLabel;
    private final Label lastHeartbeatLabel;
    private final Label taskPollingLabel;

    ConnectionStatusPane() {
        this.masterConnectionLabel = new Label();
        this.workerModeLabel = new Label();
        this.workerApiReadyLabel = new Label();
        this.apiKeyConfiguredLabel = new Label();
        this.heartbeatLabel = new Label();
        this.lastHeartbeatLabel = new Label();
        this.taskPollingLabel = new Label();

        setSpacing(8);

        GridPane grid = createGrid();
        addRow(grid, 0, Feather.WIFI, "Master connection", masterConnectionLabel);
        addRow(grid, 1, Feather.USER_CHECK, "Worker mode", workerModeLabel);
        addRow(grid, 2, Feather.CHECK_CIRCLE, "Worker API", workerApiReadyLabel);
        addRow(grid, 3, Feather.KEY, "API key configured", apiKeyConfiguredLabel);
        addRow(grid, 4, Feather.ACTIVITY, "Heartbeat", heartbeatLabel);
        addRow(grid, 5, Feather.CLOCK, "Last successful heartbeat", lastHeartbeatLabel);
        addRow(grid, 6, Feather.CPU, "Task polling", taskPollingLabel);

        getChildren().add(grid);
    }

    void refresh(AgentStateSnapshot snapshot, AgentConfig config, boolean apiKeyConfigured) {
        masterConnectionLabel.setText(masterConnectionLabel(snapshot.masterConnectionState(), config));
        applyStateClass(masterConnectionLabel, masterConnectionClass(snapshot.masterConnectionState()));

        workerModeLabel.setText(workerModeLabel(snapshot));
        applyStateClass(workerModeLabel, workerModeClass(snapshot));

        workerApiReadyLabel.setText(snapshot.workerApiReady() ? "Ready" : "Not ready");
        applyStateClass(workerApiReadyLabel, snapshot.workerApiReady() ? "state-good" : "state-warning");

        apiKeyConfiguredLabel.setText(ExecutionDisplayFormatter.yesNo(apiKeyConfigured));
        applyStateClass(apiKeyConfiguredLabel, apiKeyConfigured ? "state-good" : "state-warning");

        heartbeatLabel.setText(heartbeatLabel(snapshot.heartbeatState()));
        applyStateClass(heartbeatLabel, heartbeatClass(snapshot.heartbeatState()));

        if (snapshot.lastSuccessfulHeartbeat() == null) {
            lastHeartbeatLabel.setText("Never");
            applyStateClass(lastHeartbeatLabel, "state-warning");
        } else {
            lastHeartbeatLabel.setText(HEARTBEAT_TIME_FORMATTER.format(snapshot.lastSuccessfulHeartbeat()));
            applyStateClass(lastHeartbeatLabel, "state-good");
        }

        taskPollingLabel.setText(snapshot.taskPollingEnabled() ? "Enabled" : "Disabled");
        applyStateClass(taskPollingLabel, snapshot.taskPollingEnabled() ? "state-good" : "state-warning");
    }

    private static String masterConnectionLabel(MasterConnectionState state, AgentConfig config) {
        return switch (state) {
            case NOT_CONFIGURED -> "Not configured";
            case UNKNOWN -> config.hasMasterBaseUrl() ? "Configured" : "Not configured";
            case CONNECTED -> "Connected";
            case ATTENTION_REQUIRED -> "Attention needed";
        };
    }

    private static String masterConnectionClass(MasterConnectionState state) {
        return switch (state) {
            case CONNECTED -> "state-good";
            case ATTENTION_REQUIRED -> "state-danger";
            case NOT_CONFIGURED, UNKNOWN -> "state-warning";
        };
    }

    private static String workerModeLabel(AgentStateSnapshot snapshot) {
        if (!snapshot.workerRegistered()) {
            return "Unregistered";
        }

        return snapshot.workerMode() == WorkerMode.PAUSED ? "Paused" : "Active";
    }

    private static String workerModeClass(AgentStateSnapshot snapshot) {
        if (!snapshot.workerRegistered() || snapshot.workerMode() == WorkerMode.PAUSED) {
            return "state-warning";
        }

        return "state-good";
    }

    private static String heartbeatLabel(HeartbeatState state) {
        return switch (state) {
            case STOPPED -> "Stopped";
            case STARTING -> "Starting";
            case RUNNING -> "Running";
            case FAILED -> "Failed";
        };
    }

    private static String heartbeatClass(HeartbeatState state) {
        return switch (state) {
            case RUNNING -> "state-good";
            case FAILED -> "state-danger";
            case STOPPED, STARTING -> "state-warning";
        };
    }

    private static GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(7);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(170);

        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        return grid;
    }

    private static void addRow(GridPane grid, int row, Feather icon, String label, Label valueNode) {
        Label labelNode = new Label(label);
        labelNode.setGraphic(DashboardIcons.icon(icon, 14));
        labelNode.setGraphicTextGap(8);
        labelNode.getStyleClass().add("detail-label");

        valueNode.getStyleClass().add("detail-value");
        valueNode.setWrapText(true);

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    private static void applyStateClass(Label label, String stateClass) {
        label.getStyleClass().removeAll(STATE_CLASSES);
        label.getStyleClass().add(stateClass);
    }
}
