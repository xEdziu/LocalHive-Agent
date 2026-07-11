package dev.adrian.goral.localhiveagent.ui;

import javafx.scene.control.ButtonBase;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

final class DashboardIcons {

    private DashboardIcons() {
    }

    static FontIcon icon(Feather iconCode) {
        return icon(iconCode, 16);
    }

    static FontIcon icon(Feather iconCode, int size) {
        FontIcon icon = FontIcon.of(iconCode, size);
        icon.getStyleClass().add("dashboard-icon");
        return icon;
    }

    static void setButtonIcon(ButtonBase button, Feather iconCode) {
        button.setGraphic(icon(iconCode, 15));
        button.setGraphicTextGap(8);
    }
}
