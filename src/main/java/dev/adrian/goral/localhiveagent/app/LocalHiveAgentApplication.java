package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.desktop.AgentTrayActions;
import dev.adrian.goral.localhiveagent.desktop.AgentTrayService;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import dev.adrian.goral.localhiveagent.ui.AgentMainController;
import dev.adrian.goral.localhiveagent.ui.AgentMainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalHiveAgentApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(LocalHiveAgentApplication.class);
    private static final double WINDOW_WIDTH = 1360;
    private static final double WINDOW_HEIGHT = 900;
    private static final double MIN_WINDOW_WIDTH = 1000;
    private static final double MIN_WINDOW_HEIGHT = 700;

    private AgentRuntime runtime;
    private AgentTrayService trayService;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    @Override
    public void init() {
        this.runtime = AgentRuntime.createDefault();
    }

    @Override
    public void start(Stage stage) {
        AgentConfig config = runtime.configService().loadOrCreate();
        MachineSpec machineSpec = runtime.systemInfoProvider().collectMachineSpec(config.sharedRamMb());

        log.info("LocalHive Agent started");
        log.info("Config path: {}", runtime.configService().configPath());
        log.info("Worker registered: {}", config.hasWorkerId());
        log.info("API key configured: {}", runtime.credentialStore().hasApiKey());
        log.info("Credential store backend: {}", runtime.credentialStore().backendName());

        if (!runtime.credentialStore().isSecure()) {
            log.warn("Using insecure file-based credential storage. Install a supported system credential backend.");
        }
        log.info("Detected machine spec: {}", machineSpec);

        AgentMainView view = new AgentMainView(
                config,
                machineSpec,
                runtime.configService().configPath(),
                runtime.credentialStore().hasApiKey(),
                runtime.credentialStore().backendName(),
                runtime.credentialStore().isSecure()
        );

        AgentMainController controller = new AgentMainController(runtime, view);

        Parent root = view.createRoot();
        controller.initialize();

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(
                LocalHiveAgentApplication.class.getResource(
                        "/dev/adrian/goral/localhiveagent/ui/agent-dashboard.css"
                )
        ).toExternalForm());

        stage.setTitle("LocalHive Agent");
        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);
        stage.setScene(scene);

        configureSystemTray(stage, controller);

        stage.show();
    }

    @Override
    public void stop() {
        AgentTrayService currentTrayService = trayService;

        if (currentTrayService != null) {
            currentTrayService.close();
        }

        if (runtime != null) {
            runtime.close();
        }
    }

    private void configureSystemTray(Stage stage, AgentMainController controller) {
        AgentTrayService candidateTrayService = new AgentTrayService();

        if (!candidateTrayService.start(createTrayActions(stage, controller), runtime.agentStateStore().snapshot())) {
            return;
        }

        trayService = candidateTrayService;
        runtime.agentStateStore().addListener(candidateTrayService::updateState);
        Platform.setImplicitExit(false);

        stage.setOnCloseRequest(event -> {
            if (shutdownRequested.get()) {
                return;
            }

            event.consume();
            stage.hide();
            candidateTrayService.showDashboardHiddenNotificationOnce();
        });
    }

    private AgentTrayActions createTrayActions(Stage stage, AgentMainController controller) {
        return new AgentTrayActions() {
            @Override
            public void openDashboard() {
                Platform.runLater(() -> {
                    stage.show();
                    stage.setIconified(false);
                    stage.toFront();
                    stage.requestFocus();
                });
            }

            @Override
            public void toggleWorkerMode() {
                Platform.runLater(controller::toggleWorkerMode);
            }

            @Override
            public void exitApplication() {
                requestApplicationExit();
            }
        };
    }

    private void requestApplicationExit() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            return;
        }

        log.info("LocalHive Agent exit requested.");

        AgentTrayService currentTrayService = trayService;

        if (currentTrayService != null) {
            currentTrayService.close();
        }

        if (runtime != null) {
            runtime.close();
        }

        Platform.setImplicitExit(true);
        Platform.runLater(Platform::exit);
    }
}
