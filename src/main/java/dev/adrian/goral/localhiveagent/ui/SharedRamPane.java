package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;

class SharedRamPane extends VBox {

    private static final int RAM_STEP_MB = 512;

    private final int detectedTotalRamMb;
    private final Label currentValueLabel;
    private final TextField sharedRamMbField;
    private final Slider sharedRamSlider;
    private final Button updateAllocationButton;

    SharedRamPane(AgentConfig initialConfig, int detectedTotalRamMb) {
        this.detectedTotalRamMb = detectedTotalRamMb;
        this.currentValueLabel = new Label();
        this.sharedRamMbField = new TextField();
        this.sharedRamSlider = new Slider(
                0,
                Math.max(1024, detectedTotalRamMb),
                boundedRam(initialConfig.sharedRamMb())
        );
        this.updateAllocationButton = new Button("Save and Send Allocation");

        setSpacing(10);

        sharedRamMbField.setPromptText("8192");
        sharedRamMbField.setMaxWidth(120);
        sharedRamMbField.setText(String.valueOf(boundedRam(initialConfig.sharedRamMb())));
        sharedRamMbField.getStyleClass().add("ram-input");

        currentValueLabel.getStyleClass().add("shared-ram-value");
        updateCurrentValueLabel(boundedRam(initialConfig.sharedRamMb()));

        sharedRamSlider.setShowTickLabels(true);
        sharedRamSlider.setShowTickMarks(true);
        sharedRamSlider.setMajorTickUnit(Math.max(1024, detectedTotalRamMb / 4.0));
        sharedRamSlider.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return formatSliderTickLabel(value);
            }

            @Override
            public Double fromString(String value) {
                return 0.0;
            }
        });
        sharedRamSlider.setBlockIncrement(RAM_STEP_MB);
        sharedRamSlider.setSnapToTicks(false);
        HBox.setHgrow(sharedRamSlider, Priority.ALWAYS);

        ChangeListener<Number> sliderListener = (observable, oldValue, newValue) -> {
            int roundedValue = boundedRam(roundToStep(newValue.intValue(), RAM_STEP_MB));
            sharedRamMbField.setText(String.valueOf(roundedValue));
            updateCurrentValueLabel(roundedValue);
        };
        sharedRamSlider.valueProperty().addListener(sliderListener);

        sharedRamMbField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isBlank()) {
                updateCurrentValueLabel(0);
                return;
            }

            try {
                int parsedValue = Integer.parseInt(newValue.trim());

                if (parsedValue >= 0 && parsedValue <= detectedTotalRamMb) {
                    sharedRamSlider.setValue(parsedValue);
                    updateCurrentValueLabel(parsedValue);
                }
            } catch (NumberFormatException ignored) {
                // Invalid input is handled by AgentConfigValidator during saving.
            }
        });

        DashboardIcons.setButtonIcon(updateAllocationButton, Feather.SEND);
        updateAllocationButton.getStyleClass().add("primary-button");

        Label helper = new Label("Maximum allocation for swarm workloads");
        helper.getStyleClass().add("section-helper");

        HBox valueRow = new HBox(12);
        valueRow.setAlignment(Pos.CENTER_LEFT);
        valueRow.getChildren().addAll(new Label("Current allocation"), currentValueLabel);
        valueRow.getStyleClass().add("shared-ram-summary");

        HBox inputRow = new HBox(10);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        Label inputLabel = new Label("MB");
        inputLabel.getStyleClass().add("detail-label");
        inputRow.getChildren().addAll(sharedRamMbField, inputLabel, updateAllocationButton);

        getChildren().addAll(helper, valueRow, sharedRamSlider, inputRow);
    }

    String sharedRamMbInput() {
        return sharedRamMbField.getText();
    }

    Button updateAllocationButton() {
        return updateAllocationButton;
    }

    void refreshSharedRam(int sharedRamMb) {
        int boundedValue = boundedRam(sharedRamMb);
        sharedRamMbField.setText(String.valueOf(boundedValue));
        sharedRamSlider.setValue(boundedValue);
        updateCurrentValueLabel(boundedValue);
    }

    private int boundedRam(int sharedRamMb) {
        return Math.max(0, Math.min(sharedRamMb, detectedTotalRamMb));
    }

    private void updateCurrentValueLabel(int sharedRamMb) {
        currentValueLabel.setText(ResourceOverviewPane.formatRam(sharedRamMb) + " / "
                + ResourceOverviewPane.formatRam(detectedTotalRamMb));
    }

    private String formatSliderTickLabel(Double value) {
        if (value == null) {
            return "";
        }

        double maxRamMb = Math.max(1024, detectedTotalRamMb);

        if (value <= RAM_STEP_MB / 2.0) {
            return "0 GB";
        }

        if (Math.abs(value - detectedTotalRamMb) <= Math.max(RAM_STEP_MB / 2.0, maxRamMb * 0.02)) {
            return ResourceOverviewPane.formatRam(detectedTotalRamMb);
        }

        double roundedGb = Math.round((value / 1024.0) / sliderLabelStepGb(maxRamMb)) * sliderLabelStepGb(maxRamMb);

        if (Math.abs(roundedGb - Math.rint(roundedGb)) < 0.05) {
            return String.format("%.0f GB", roundedGb);
        }

        return String.format("%.1f GB", roundedGb);
    }

    private static double sliderLabelStepGb(double maxRamMb) {
        double maxGb = maxRamMb / 1024.0;

        if (maxGb >= 48) {
            return 16;
        }

        if (maxGb >= 24) {
            return 8;
        }

        if (maxGb >= 12) {
            return 4;
        }

        if (maxGb >= 6) {
            return 2;
        }

        return 1;
    }

    private static int roundToStep(int value, int step) {
        if (step <= 0) {
            return value;
        }

        return Math.round((float) value / step) * step;
    }
}
