package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

class GamerModePane extends VBox {

    private final StatusBadge pauseStatusBadge;
    private final Button pauseResumeButton;

    GamerModePane(AgentConfig initialConfig) {
        this.pauseStatusBadge = new StatusBadge(Feather.PLAY_CIRCLE, "Active", StatusBadge.SUCCESS);
        this.pauseResumeButton = new Button();

        setSpacing(10);

        Label helper = new Label("Pause swarm assignments while this machine is in personal use");
        helper.setWrapText(true);
        helper.getStyleClass().add("section-helper");

        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(new Label("Worker state"), pauseStatusBadge);

        pauseResumeButton.getStyleClass().add("primary-button");

        getChildren().addAll(helper, statusRow, pauseResumeButton);

        refresh(initialConfig.pauseEnabled());
    }

    Button pauseResumeButton() {
        return pauseResumeButton;
    }

    void refresh(boolean pauseEnabled) {
        if (pauseEnabled) {
            pauseStatusBadge.setStatus(Feather.PAUSE_CIRCLE, "Paused", StatusBadge.WARNING);
            pauseResumeButton.setText("Resume Worker");
            DashboardIcons.setButtonIcon(pauseResumeButton, Feather.PLAY);
            return;
        }

        pauseStatusBadge.setStatus(Feather.PLAY_CIRCLE, "Active", StatusBadge.SUCCESS);
        pauseResumeButton.setText("Pause Worker");
        DashboardIcons.setButtonIcon(pauseResumeButton, Feather.PAUSE);
    }
}
