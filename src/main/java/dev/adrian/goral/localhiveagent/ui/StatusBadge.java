package dev.adrian.goral.localhiveagent.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

class StatusBadge extends HBox {

    static final String NEUTRAL = "status-neutral";
    static final String SUCCESS = "status-success";
    static final String WARNING = "status-warning";
    static final String DANGER = "status-danger";

    private static final List<String> STATUS_CLASSES = List.of(
            NEUTRAL,
            SUCCESS,
            WARNING,
            DANGER
    );

    private final FontIcon icon;
    private final Label label;

    StatusBadge(Feather iconCode, String text, String statusClass) {
        this.icon = DashboardIcons.icon(iconCode, 14);
        this.label = new Label();

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(7);
        getStyleClass().add("status-badge");
        getChildren().addAll(icon, label);

        setStatus(iconCode, text, statusClass);
    }

    void setStatus(Feather iconCode, String text, String statusClass) {
        icon.setIconCode(iconCode);
        label.setText(text);
        getStyleClass().removeAll(STATUS_CLASSES);
        getStyleClass().add(statusClass);
    }
}
