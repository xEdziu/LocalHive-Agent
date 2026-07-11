package dev.adrian.goral.localhiveagent.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

class MaintenanceActionsPane extends VBox {

    private final Button heartbeatNowButton;
    private final Button startHeartbeatButton;
    private final Button stopHeartbeatButton;
    private final Button updateHardwareSpecButton;

    MaintenanceActionsPane() {
        this.heartbeatNowButton = createActionButton("Send Heartbeat Now", Feather.ACTIVITY);
        this.startHeartbeatButton = createActionButton("Start Heartbeat", Feather.POWER);
        this.stopHeartbeatButton = createActionButton("Stop Heartbeat", Feather.STOP_CIRCLE);
        this.updateHardwareSpecButton = createActionButton("Update Hardware Spec", Feather.REFRESH_CW);

        setSpacing(10);

        Label helper = new Label("Manual operations for diagnostics and maintenance.");
        helper.setWrapText(true);
        helper.getStyleClass().add("section-helper");

        VBox heartbeatGroup = createActionGroup(
                "Heartbeat",
                "Scheduler control and one-off connectivity checks.",
                heartbeatNowButton,
                startHeartbeatButton,
                stopHeartbeatButton
        );

        VBox hardwareGroup = createActionGroup(
                "Hardware",
                "Refresh the hardware profile stored by Master.",
                updateHardwareSpecButton
        );

        getChildren().addAll(helper, heartbeatGroup, hardwareGroup);
    }

    Button heartbeatNowButton() {
        return heartbeatNowButton;
    }

    Button startHeartbeatButton() {
        return startHeartbeatButton;
    }

    Button stopHeartbeatButton() {
        return stopHeartbeatButton;
    }

    Button updateHardwareSpecButton() {
        return updateHardwareSpecButton;
    }

    private static VBox createActionGroup(String title, String description, Button... buttons) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("action-group-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("action-group-description");

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("action-row");
        row.getChildren().addAll(buttons);

        VBox group = new VBox(6, titleLabel, descriptionLabel, row);
        group.getStyleClass().add("action-group");

        return group;
    }

    private static Button createActionButton(String text, Feather iconCode) {
        Button button = new Button(text);
        DashboardIcons.setButtonIcon(button, iconCode);
        button.getStyleClass().add("secondary-button");
        return button;
    }
}
