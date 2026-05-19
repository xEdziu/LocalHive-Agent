package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class LocalHiveAgentApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(LocalHiveAgentApplication.class);

    private ConfigService configService;

    @Override
    public void init() {
        Path configPath = Path.of(System.getProperty("user.home"), ".localhive-agent", "config.json");
        this.configService = new ConfigService(configPath);
    }

    @Override
    public void start(Stage stage) {
        AgentConfig config = configService.loadOrCreate();

        log.info("LocalHive Agent started");
        log.info("Config path: {}", configService.configPath());
        log.info("Worker registered: {}", config.hasWorkerId());
        log.info("API key configured: {}", config.hasApiKey());

        VBox root = new VBox(12);
        root.setStyle("-fx-padding: 24;");
        root.getChildren().addAll(
                new Label("LocalHive Agent"),
                new Label("Config: " + configService.configPath()),
                new Label("Worker ID: " + (config.hasWorkerId() ? config.workerId().toString() : "not registered")),
                new Label("API Key: " + (config.hasApiKey() ? "configured" : "missing")),
                new Label("Shared RAM: " + config.sharedRamMb() + " MB"),
                new Label("Paused: " + config.pauseEnabled())
        );

        Scene scene = new Scene(root, 640, 360);

        stage.setTitle("LocalHive Agent");
        stage.setScene(scene);
        stage.show();
    }
}