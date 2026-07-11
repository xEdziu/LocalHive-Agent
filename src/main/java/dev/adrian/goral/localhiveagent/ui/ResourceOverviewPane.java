package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.feather.Feather;

class ResourceOverviewPane extends VBox {

    private static final double RESOURCE_TILE_ASPECT_RATIO = 0.36;
    private static final double MIN_RESOURCE_TILE_HEIGHT = 120;
    private static final Color TILE_BACKGROUND = Color.web("#18181b");
    private static final Color TILE_BORDER = Color.web("#34343a");
    private static final Color TILE_FOREGROUND = Color.web("#d8d8de");
    private static final Color TILE_MUTED = Color.web("#a1a1aa");
    private static final Color TILE_TRACK = Color.web("#2a2a30");
    private static final Color RAM_COLOR = Color.web("#f5c542");
    private static final Color SHARED_RAM_COLOR = Color.web("#22c55e");
    private static final Color CPU_COLOR = Color.web("#38bdf8");

    private final MachineSpec machineSpec;
    private final Tile sharedRamTile;

    ResourceOverviewPane(AgentConfig initialConfig, MachineSpec machineSpec) {
        this.machineSpec = machineSpec;
        this.sharedRamTile = createSharedRamTile(initialConfig.sharedRamMb());

        setSpacing(8);

        getChildren().addAll(createResourceTileGrid(), createMachineDetailsGrid());
    }

    void setSharedRamMb(int sharedRamMb) {
        int boundedSharedRamMb = Math.max(0, Math.min(sharedRamMb, machineSpec.totalRamMb()));
        sharedRamTile.setValue(toRamPercent(boundedSharedRamMb));
        sharedRamTile.setDescription(formatSharedRamDescription(boundedSharedRamMb));
    }

    private VBox createTotalRamTile() {
        return createMetricTile(
                "Total RAM",
                String.format("%.1f", toGb(machineSpec.totalRamMb())),
                "GB",
                "metric-value-ram",
                formatRam(machineSpec.totalRamMb()) + " detected by OSHI"
        );
    }

    private Tile createSharedRamTile(int sharedRamMb) {
        int boundedSharedRamMb = Math.max(0, Math.min(sharedRamMb, machineSpec.totalRamMb()));

        return TileBuilder.create()
                .skinType(Tile.SkinType.BAR_GAUGE)
                .title("Shared RAM")
                .unit("%")
                .value(toRamPercent(boundedSharedRamMb))
                .minValue(0)
                .maxValue(100)
                .autoScale(false)
                .decimals(0)
                .description(formatSharedRamDescription(boundedSharedRamMb))
                .descriptionAlignment(Pos.BOTTOM_LEFT)
                .backgroundColor(TILE_BACKGROUND)
                .foregroundColor(SHARED_RAM_COLOR)
                .titleColor(TILE_MUTED)
                .descriptionColor(TILE_FOREGROUND)
                .valueColor(TILE_FOREGROUND)
                .unitColor(TILE_MUTED)
                .barColor(SHARED_RAM_COLOR)
                .barBackgroundColor(TILE_TRACK)
                .borderColor(TILE_BORDER)
                .borderWidth(1)
                .roundedCorners(true)
                .animated(false)
                .textSize(Tile.TextSize.BIGGER)
                .build();
    }

    private VBox createCpuTile() {
        return createMetricTile(
                "CPU",
                String.valueOf(machineSpec.cpuCores()),
                "logical cores",
                "metric-value-cpu",
                "Available processor threads"
        );
    }

    private VBox createMetricTile(
            String title,
            String value,
            String unit,
            String valueStyleClass,
            String description
    ) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("resource-metric-title");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().addAll("resource-metric-value", valueStyleClass);

        Label unitLabel = new Label(unit);
        unitLabel.getStyleClass().add("resource-metric-unit");

        HBox valueRow = new HBox(3, valueLabel, unitLabel);
        valueRow.setAlignment(Pos.BASELINE_CENTER);
        valueRow.getStyleClass().add("resource-metric-value-row");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("resource-metric-description");

        VBox tile = new VBox(8, titleLabel, valueRow, spacer, descriptionLabel);
        tile.getStyleClass().add("resource-metric-tile");
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMinHeight(MIN_RESOURCE_TILE_HEIGHT);

        return tile;
    }

    private GridPane createResourceTileGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.getStyleClass().add("resource-tile-grid");

        for (int columnIndex = 0; columnIndex < 3; columnIndex++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3.0);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }

        addResourceTile(grid, createTotalRamTile(), 0);
        addResourceTile(grid, sharedRamTile, 1);
        addResourceTile(grid, createCpuTile(), 2);

        return grid;
    }

    private static void addResourceTile(GridPane grid, Region tile, int columnIndex) {
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMaxHeight(Double.MAX_VALUE);
        tile.setMinHeight(MIN_RESOURCE_TILE_HEIGHT);
        tile.setPrefHeight(MIN_RESOURCE_TILE_HEIGHT);
        tile.widthProperty().addListener((observable, oldValue, newValue) ->
                updateResourceTileHeight(tile, newValue.doubleValue())
        );

        GridPane.setHgrow(tile, Priority.ALWAYS);
        GridPane.setVgrow(tile, Priority.ALWAYS);

        grid.add(tile, columnIndex, 0);
    }

    private static void updateResourceTileHeight(Region tile, double width) {
        if (width <= 0) {
            return;
        }

        tile.setPrefHeight(Math.max(MIN_RESOURCE_TILE_HEIGHT, width * RESOURCE_TILE_ASPECT_RATIO));
    }

    private FlowPane createMachineDetailsGrid() {
        FlowPane detailsPane = new FlowPane();
        detailsPane.setHgap(24);
        detailsPane.setVgap(8);
        detailsPane.setPadding(new Insets(2, 0, 0, 0));
        detailsPane.getStyleClass().add("machine-details-grid");

        detailsPane.getChildren().addAll(
                createDetailItem(Feather.MONITOR, "Hostname", machineSpec.hostname()),
                createDetailItem(Feather.WIFI, "IP address", machineSpec.ipAddress()),
                createDetailItem(Feather.SERVER, "Operating system", machineSpec.osType()),
                createDetailItem(Feather.HARD_DRIVE, "GPU", machineSpec.gpuName().isBlank()
                ? "not detected"
                : machineSpec.gpuName())
        );

        return detailsPane;
    }

    private static HBox createDetailItem(Feather icon, String label, String value) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("machine-detail-item");

        Label labelNode = new Label(label);
        labelNode.setGraphic(DashboardIcons.icon(icon, 14));
        labelNode.setGraphicTextGap(8);
        labelNode.getStyleClass().add("detail-label");

        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        valueNode.setMaxWidth(260);
        valueNode.getStyleClass().add("detail-value");

        item.getChildren().addAll(labelNode, valueNode);

        return item;
    }

    static String formatRam(int megabytes) {
        if (megabytes >= 1024) {
            return String.format("%.1f GB", megabytes / 1024.0);
        }

        return megabytes + " MB";
    }

    private static double toGb(int megabytes) {
        return megabytes / 1024.0;
    }

    private double toRamPercent(int sharedRamMb) {
        if (machineSpec.totalRamMb() <= 0) {
            return 0;
        }

        return Math.max(0, Math.min(100, sharedRamMb * 100.0 / machineSpec.totalRamMb()));
    }

    private String formatSharedRamDescription(int sharedRamMb) {
        return formatRam(sharedRamMb) + " / " + formatRam(machineSpec.totalRamMb()) + " shared with the swarm";
    }
}
