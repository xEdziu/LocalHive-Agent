package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.logging.AgentLogPolicy;
import dev.adrian.goral.localhiveagent.logging.AgentLogging;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        AgentLogPolicy logPolicy = AgentLogPolicy.defaultPolicy();
        AgentLogging.initialize(logPolicy);

        Logger log = LoggerFactory.getLogger(Launcher.class);
        log.info("Application starting");
        log.info("LocalHive Agent starting");
        log.info("Java version: {}", System.getProperty("java.version", "unknown"));
        log.info("Operating system: {} {}", System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"));
        log.info("File logging enabled: {}", AgentLogging.isFileLoggingEnabled());
        log.info("Log directory: {}", logPolicy.logDirectory());
        log.info("Log retention: maxFileSizeBytes={}, maxFileCount={}, expectedDiskUsageBytes={}",
                logPolicy.maxFileSizeBytes(),
                logPolicy.maxFileCount(),
                logPolicy.maximumExpectedDiskUsageBytes());

        Application.launch(LocalHiveAgentApplication.class, args);
    }
}
