package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.app.AgentPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public final class OutputDirectoryService implements OutputDirectoryPreparer {

    @Override
    public PreparedOutputDirectory prepare(UUID executionId) {
        Objects.requireNonNull(executionId, "executionId is required");

        Path agentDirectory = AgentPaths.agentDirectory();
        Path outputDirectory = AgentPaths.executionOutputDirectory(executionId);

        try {
            WorkspacePathGuard.createDirectoriesUnder(agentDirectory, outputDirectory);
            WorkspacePathGuard.ensureNoExistingSymlinksUnder(agentDirectory, outputDirectory, false);
        } catch (IOException exception) {
            throw new OutputDirectoryPreparationException("Failed to create execution output directory.", exception);
        } catch (WorkspaceUnpackException exception) {
            throw new OutputDirectoryInvalidException("Execution output directory is unsafe.", exception);
        }

        return new PreparedOutputDirectory(outputDirectory);
    }
}
