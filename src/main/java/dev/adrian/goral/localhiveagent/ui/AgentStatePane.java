package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.nio.file.Path;
import java.util.List;

class AgentStatePane extends VBox {

    private static final List<String> STATE_CLASSES = List.of(
            "state-good",
            "state-warning",
            "state-danger"
    );

    private final TextField masterBaseUrlField;
    private final PasswordField apiKeyField;
    private final Label workerIdLabel;
    private final Label apiKeyLabel;
    private final Label backendLabel;
    private final Label configPathLabel;
    private final Label statusLabel;
    private final Button saveConfigButton;
    private final Button registerButton;

    AgentStatePane(
            AgentConfig initialConfig,
            Path configPath,
            boolean initialApiKeyConfigured,
            String credentialBackendName,
            boolean credentialBackendSecure
    ) {
        this.masterBaseUrlField = new TextField(initialConfig.masterBaseUrl());
        this.apiKeyField = new PasswordField();
        this.workerIdLabel = new Label();
        this.apiKeyLabel = new Label();
        this.backendLabel = new Label(formatCredentialBackend(
                credentialBackendName,
                credentialBackendSecure
        ));
        this.configPathLabel = new Label(configPath.toString());
        this.statusLabel = new Label("Ready.");
        this.saveConfigButton = new Button("Save Agent Settings");
        this.registerButton = new Button("Register Worker");

        setSpacing(10);

        masterBaseUrlField.setPromptText("http://localhost:8080");
        HBox.setHgrow(masterBaseUrlField, Priority.ALWAYS);

        apiKeyField.setPromptText(initialApiKeyConfigured
                ? "API key is configured"
                : "Paste API key after approval");

        configPathLabel.setWrapText(true);
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("status-message");

        DashboardIcons.setButtonIcon(saveConfigButton, Feather.SAVE);
        saveConfigButton.getStyleClass().add("secondary-button");
        DashboardIcons.setButtonIcon(registerButton, Feather.USER_PLUS);
        registerButton.getStyleClass().add("primary-button");

        GridPane grid = createGrid();
        addRow(grid, 0, Feather.SERVER, "Master URL", masterBaseUrlField);
        addRow(grid, 1, Feather.USER_CHECK, "Worker ID", workerIdLabel);
        addRow(grid, 2, Feather.KEY, "API key", apiKeyLabel);
        addRow(grid, 3, Feather.LOCK, "Credential backend", backendLabel);
        addRow(grid, 4, Feather.FILE_TEXT, "Config path", configPathLabel);
        addRow(grid, 5, Feather.KEY, "New API key", apiKeyField);

        Label messageCaption = new Label("Last message");
        messageCaption.getStyleClass().add("detail-label");

        HBox actionRow = new HBox(10, saveConfigButton, registerButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(grid, actionRow, messageCaption, statusLabel);

        refreshConfig(initialConfig, initialApiKeyConfigured);
    }

    String masterBaseUrlInput() {
        return masterBaseUrlField.getText();
    }

    String apiKeyInput() {
        return apiKeyField.getText();
    }

    void clearApiKeyInput() {
        apiKeyField.clear();
    }

    Button saveConfigButton() {
        return saveConfigButton;
    }

    Button registerButton() {
        return registerButton;
    }

    void setStatus(String status) {
        String displayStatus = status == null || status.isBlank() ? "Ready." : status;
        statusLabel.setText(displayStatus);
        statusLabel.getStyleClass().removeAll("status-message-error", "status-message-success");

        String normalizedStatus = displayStatus.toLowerCase();
        if (normalizedStatus.contains("failed") || normalizedStatus.contains("cannot")) {
            statusLabel.getStyleClass().add("status-message-error");
            return;
        }

        statusLabel.getStyleClass().add("status-message-success");
    }

    void refreshConfig(AgentConfig config, boolean apiKeyConfigured) {
        if (!masterBaseUrlField.isFocused()) {
            masterBaseUrlField.setText(config.masterBaseUrl());
        }

        workerIdLabel.setText(config.hasWorkerId()
                ? config.workerId().toString()
                : "not registered");

        apiKeyLabel.setText(apiKeyConfigured ? "configured" : "missing");
        applyStateClass(apiKeyLabel, apiKeyConfigured ? "state-good" : "state-warning");
    }

    private static GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(7);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);

        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        return grid;
    }

    private static void addRow(GridPane grid, int row, Feather icon, String label, javafx.scene.Node valueNode) {
        Label labelNode = new Label(label);
        labelNode.setGraphic(DashboardIcons.icon(icon, 14));
        labelNode.setGraphicTextGap(8);
        labelNode.getStyleClass().add("detail-label");

        if (valueNode instanceof Label labelValue) {
            labelValue.getStyleClass().add("detail-value");
            labelValue.setWrapText(true);
        }

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    private static String formatCredentialBackend(String backendName, boolean secure) {
        String securityLabel = secure ? "secure" : "insecure fallback";
        return backendName + " (" + securityLabel + ")";
    }

    private static void applyStateClass(Label label, String stateClass) {
        label.getStyleClass().removeAll(STATE_CLASSES);
        label.getStyleClass().add(stateClass);
    }
}
