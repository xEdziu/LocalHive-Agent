package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

class DockerPolicyPane extends VBox {

    private final Label enabledLabel;
    private final Label allowedImagesLabel;
    private final Label maxMemoryLabel;
    private final Label maxCpuLabel;
    private final Label gpuAllowedLabel;

    DockerPolicyPane(DockerPolicy initialPolicy) {
        this.enabledLabel = new Label();
        this.allowedImagesLabel = new Label();
        this.maxMemoryLabel = new Label();
        this.maxCpuLabel = new Label();
        this.gpuAllowedLabel = new Label();

        setSpacing(8);

        GridPane grid = createGrid();
        addRow(grid, 0, Feather.CHECK_CIRCLE, "Docker enabled", enabledLabel);
        addRow(grid, 1, Feather.LIST, "Allowed images", allowedImagesLabel);
        addRow(grid, 2, Feather.HARD_DRIVE, "Max memory", maxMemoryLabel);
        addRow(grid, 3, Feather.CPU, "Max CPU cores", maxCpuLabel);
        addRow(grid, 4, Feather.MONITOR, "GPU allowed", gpuAllowedLabel);

        getChildren().add(grid);
        refresh(initialPolicy);
    }

    void refresh(DockerPolicy policy) {
        DockerPolicyViewModel viewModel = DockerPolicyViewModel.from(policy);
        enabledLabel.setText(viewModel.enabled());
        allowedImagesLabel.setText(viewModel.allowedImages());
        maxMemoryLabel.setText(viewModel.maxMemoryMb());
        maxCpuLabel.setText(viewModel.maxCpuCores());
        gpuAllowedLabel.setText(viewModel.gpuAllowed());
    }

    private static GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(7);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(140);

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
}
