package dev.adrian.goral.localhiveagent.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

class TaskHistoryPane extends VBox {

    private final Label titleLabel;
    private final StatusBadge statusBadge;
    private final Label countLabel;
    private final Label executorLabel;
    private final Label technicalExecutorLabel;
    private final Label durationLabel;
    private final Label timestampLabel;
    private final Label issueLabel;

    TaskHistoryPane() {
        this.titleLabel = new Label("No task history yet");
        this.statusBadge = new StatusBadge(Feather.CLOCK, "-", StatusBadge.NEUTRAL);
        this.countLabel = new Label();
        this.executorLabel = new Label();
        this.technicalExecutorLabel = new Label();
        this.durationLabel = new Label();
        this.timestampLabel = new Label();
        this.issueLabel = new Label();

        setSpacing(8);

        titleLabel.getStyleClass().add("execution-title");
        titleLabel.setWrapText(true);

        HBox header = new HBox(10, titleLabel, statusBadge);
        header.getStyleClass().add("execution-header");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        GridPane grid = createGrid();
        addRow(grid, 0, Feather.LIST, "History", countLabel);
        addRow(grid, 1, Feather.CPU, "Executor", executorLabel);
        addRow(grid, 2, Feather.FILE_TEXT, "Technical ID", technicalExecutorLabel);
        addRow(grid, 3, Feather.CLOCK, "Duration", durationLabel);
        addRow(grid, 4, Feather.CALENDAR, "Timestamp", timestampLabel);
        addRow(grid, 5, Feather.ALERT_CIRCLE, "Issue", issueLabel);

        getChildren().addAll(header, grid);
        refresh(TaskHistorySummaryViewModel.empty(0));
    }

    void refresh(TaskHistorySummaryViewModel viewModel) {
        titleLabel.setText(viewModel.title());
        titleLabel.getStyleClass().removeAll("execution-empty");
        if (!viewModel.present()) {
            titleLabel.getStyleClass().add("execution-empty");
        }

        statusBadge.setStatus(statusIcon(viewModel.status()), viewModel.status(), statusClass(viewModel.status()));
        countLabel.setText(viewModel.totalCountLabel());
        executorLabel.setText(viewModel.executorLabel());
        technicalExecutorLabel.setText(viewModel.executorTechnicalInfo());
        durationLabel.setText(viewModel.duration());
        timestampLabel.setText(viewModel.timestamp());
        issueLabel.setText(viewModel.issue());
    }

    private static Feather statusIcon(String status) {
        return switch (status) {
            case "RUNNING" -> Feather.ACTIVITY;
            case "CLAIMED" -> Feather.CLOCK;
            case "SUCCEEDED" -> Feather.CHECK_CIRCLE;
            case "FAILED", "ERROR" -> Feather.ALERT_CIRCLE;
            default -> Feather.CLOCK;
        };
    }

    private static String statusClass(String status) {
        return switch (status) {
            case "RUNNING", "SUCCEEDED" -> StatusBadge.SUCCESS;
            case "FAILED", "ERROR" -> StatusBadge.DANGER;
            case "CLAIMED" -> StatusBadge.WARNING;
            default -> StatusBadge.NEUTRAL;
        };
    }

    private static GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(7);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(130);

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
