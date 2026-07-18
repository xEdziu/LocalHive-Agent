package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.app.AgentPaths;
import dev.adrian.goral.localhiveagent.master.MasterTaskClient;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class WorkspaceArtifactService implements WorkspacePreparer {

    private final MasterTaskClient taskClient;
    private final WorkspacePackageUnpacker unpacker;

    public WorkspaceArtifactService(MasterTaskClient taskClient, WorkspacePackageUnpacker unpacker) {
        this.taskClient = Objects.requireNonNull(taskClient, "taskClient is required");
        this.unpacker = Objects.requireNonNull(unpacker, "unpacker is required");
    }

    public WorkspaceArtifactService() {
        this(new MasterTaskClient(), new WorkspacePackageUnpacker());
    }

    @Override
    public PreparedWorkspace prepare(AgentExecutionContext context,
                                     ClaimedExecutionPayload payload,
                                     DockerWorkspaceConfig workspace) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(workspace, "workspace is required");

        Path executionWorkspaceDirectory = AgentPaths.executionWorkspaceDirectory(payload.executionId());
        Path packagePath = AgentPaths.executionWorkspacePackagePath(payload.executionId());
        Path workspaceDirectory = AgentPaths.executionWorkspaceUnpackDirectory(payload.executionId());
        Path agentDirectory = AgentPaths.agentDirectory();

        try {
            WorkspacePathGuard.createDirectoriesUnder(agentDirectory, executionWorkspaceDirectory);
            WorkspacePathGuard.ensureNoExistingSymlinksUnder(agentDirectory, packagePath, true);
            WorkspacePathGuard.ensureNoExistingSymlinksUnder(agentDirectory, workspaceDirectory, false);
        } catch (IOException exception) {
            throw new WorkspaceUnpackException("Failed to create execution workspace directory.", exception);
        }

        taskClient.downloadExecutionArtifact(
                context.masterBaseUrl(),
                context.workerId(),
                payload.executionId(),
                workspace.artifactId(),
                context.apiKey(),
                payload.leaseToken(),
                packagePath
        );
        WorkspacePathGuard.ensureNoExistingSymlinksUnder(agentDirectory, packagePath, true);
        WorkspacePathGuard.ensureNoExistingSymlinksUnder(agentDirectory, workspaceDirectory, false);
        unpacker.unpack(packagePath, workspaceDirectory);
        return new PreparedWorkspace(workspaceDirectory);
    }
}
