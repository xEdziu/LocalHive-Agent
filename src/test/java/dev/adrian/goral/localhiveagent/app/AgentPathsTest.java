package dev.adrian.goral.localhiveagent.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPathsTest {

    @Test
    void taskHistoryPathLivesUnderAgentDirectory() {
        Path historyPath = AgentPaths.taskHistoryPath();

        assertTrue(historyPath.endsWith(Path.of(".localhive-agent", "task-history.sqlite")));
        assertEquals(AgentPaths.agentDirectory(), historyPath.getParent());
    }

    @Test
    void workspacePathsLiveUnderAgentWorkspaceDirectory() {
        UUID executionId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        assertTrue(AgentPaths.executionWorkspaceDirectory(executionId)
                .endsWith(Path.of(".localhive-agent", "workspaces", executionId.toString())));
        assertTrue(AgentPaths.executionWorkspacePackagePath(executionId)
                .endsWith(Path.of(".localhive-agent", "workspaces", executionId.toString(), "package.zip")));
        assertTrue(AgentPaths.executionWorkspaceUnpackDirectory(executionId)
                .endsWith(Path.of(".localhive-agent", "workspaces", executionId.toString(), "workspace")));
    }

    @Test
    void outputPathsLiveUnderAgentOutputDirectory() {
        UUID executionId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        assertTrue(AgentPaths.outputsDirectory()
                .endsWith(Path.of(".localhive-agent", "outputs")));
        assertTrue(AgentPaths.executionOutputRootDirectory(executionId)
                .endsWith(Path.of(".localhive-agent", "outputs", executionId.toString())));
        assertTrue(AgentPaths.executionOutputDirectory(executionId)
                .endsWith(Path.of(".localhive-agent", "outputs", executionId.toString(), "output")));
    }
}
