package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import dev.adrian.goral.localhiveagent.system.OshiSystemInfoProvider;
import dev.adrian.goral.localhiveagent.system.SystemInfoProvider;
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
    private SystemInfoProvider systemInfoProvider;

    @Override
    public void init() {
        Path configPath = Path.of(System.getProperty("user.home"), ".localhive-agent", "config.json");

        this.configService = new ConfigService(configPath);
        this.systemInfoProvider = new OshiSystemInfoProvider();
    }

    @Override
    public void start(Stage stage) {
        AgentConfig config = configService.loadOrCreate();
        MachineSpec machineSpec = systemInfoProvider.collectMachineSpec(config.sharedRamMb());

        log.info("LocalHive Agent started");
        log.info("Config path: {}", configService.configPath());
        log.info("Worker registered: {}", config.hasWorkerId());
        log.info("API key configured: {}", config.hasApiKey());
        log.info("Detected machine spec: {}", machineSpec);

        VBox root = new VBox(12);
        root.setStyle("-fx-padding: 24;");
        root.getChildren().addAll(
                new Label("LocalHive Agent"),
                new Label("Config: " + configService.configPath()),
                new Label("Worker ID: " + (config.hasWorkerId() ? config.workerId().toString() : "not registered")),
                new Label("API Key: " + (config.hasApiKey() ? "configured" : "missing")),
                new Label("Shared RAM: " + machineSpec.sharedRamMb() + " MB"),
                new Label("Paused: " + config.pauseEnabled()),
                new Label(""),
                new Label("Hostname: " + machineSpec.hostname()),
                new Label("IP address: " + machineSpec.ipAddress()),
                new Label("OS: " + machineSpec.osType()),
                new Label("Total RAM: " + machineSpec.totalRamMb() + " MB"),
                new Label("CPU cores: " + machineSpec.cpuCores()),
                new Label("GPU: " + (machineSpec.gpuName().isBlank() ? "not detected" : machineSpec.gpuName()))
        );

        Scene scene = new Scene(root, 720, 480);

        stage.setTitle("LocalHive Agent");
        stage.setScene(scene);
        stage.show();
    }
}