package dev.adrian.goral.localhiveagent.app;

import java.nio.file.Path;

public final class AgentPaths {

    private static final String AGENT_DIRECTORY_NAME = ".localhive-agent";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String LOGS_DIRECTORY_NAME = "logs";
    private static final String TASK_HISTORY_FILE_NAME = "task-history.sqlite";

    private AgentPaths() {
    }

    public static Path agentDirectory() {
        return Path.of(System.getProperty("user.home"), AGENT_DIRECTORY_NAME);
    }

    public static Path configPath() {
        return agentDirectory().resolve(CONFIG_FILE_NAME);
    }

    public static Path logsDirectory() {
        return agentDirectory().resolve(LOGS_DIRECTORY_NAME);
    }

    public static Path taskHistoryPath() {
        return agentDirectory().resolve(TASK_HISTORY_FILE_NAME);
    }
}
