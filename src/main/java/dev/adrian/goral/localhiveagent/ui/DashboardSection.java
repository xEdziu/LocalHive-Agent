package dev.adrian.goral.localhiveagent.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

class DashboardSection extends VBox {

    DashboardSection(Feather iconCode, String title, Node content) {
        setSpacing(10);
        getStyleClass().add("dashboard-section");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("section-header");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        header.getChildren().addAll(DashboardIcons.icon(iconCode, 17), titleLabel);

        VBox.setVgrow(content, Priority.ALWAYS);
        getChildren().addAll(header, content);
    }
}
