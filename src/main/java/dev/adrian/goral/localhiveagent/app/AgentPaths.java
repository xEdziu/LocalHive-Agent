package dev.adrian.goral.localhiveagent.app;

import java.nio.file.Path;
import java.util.UUID;

public final class AgentPaths {

    private static final String AGENT_DIRECTORY_NAME = ".localhive-agent";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String LOGS_DIRECTORY_NAME = "logs";
    private static final String TASK_HISTORY_FILE_NAME = "task-history.sqlite";
    private static final String WORKSPACES_DIRECTORY_NAME = "workspaces";
    private static final String WORKSPACE_PACKAGE_FILE_NAME = "package.zip";
    private static final String WORKSPACE_UNPACK_DIRECTORY_NAME = "workspace";
    private static final String OUTPUTS_DIRECTORY_NAME = "outputs";
    private static final String EXECUTION_OUTPUT_DIRECTORY_NAME = "output";

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

    public static Path workspacesDirectory() {
        return agentDirectory().resolve(WORKSPACES_DIRECTORY_NAME);
    }

    public static Path executionWorkspaceDirectory(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId is required");
        }
        return workspacesDirectory().resolve(executionId.toString());
    }

    public static Path executionWorkspacePackagePath(UUID executionId) {
        return executionWorkspaceDirectory(executionId).resolve(WORKSPACE_PACKAGE_FILE_NAME);
    }

    public static Path executionWorkspaceUnpackDirectory(UUID executionId) {
        return executionWorkspaceDirectory(executionId).resolve(WORKSPACE_UNPACK_DIRECTORY_NAME);
    }

    public static Path outputsDirectory() {
        return agentDirectory().resolve(OUTPUTS_DIRECTORY_NAME);
    }

    public static Path executionOutputRootDirectory(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId is required");
        }
        return outputsDirectory().resolve(executionId.toString());
    }

    public static Path executionOutputDirectory(UUID executionId) {
        return executionOutputRootDirectory(executionId).resolve(EXECUTION_OUTPUT_DIRECTORY_NAME);
    }
}
