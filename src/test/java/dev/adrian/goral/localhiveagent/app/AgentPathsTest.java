package dev.adrian.goral.localhiveagent.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPathsTest {

    @Test
    void taskHistoryPathLivesUnderAgentDirectory() {
        Path historyPath = AgentPaths.taskHistoryPath();

        assertTrue(historyPath.endsWith(Path.of(".localhive-agent", "task-history.sqlite")));
        assertEquals(AgentPaths.agentDirectory(), historyPath.getParent());
    }
}
