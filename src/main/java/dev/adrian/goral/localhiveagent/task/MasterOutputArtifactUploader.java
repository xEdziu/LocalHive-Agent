package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.MasterTaskClient;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

import java.util.List;
import java.util.Objects;

public final class MasterOutputArtifactUploader implements OutputArtifactUploader {

    private final MasterTaskClient taskClient;

    public MasterOutputArtifactUploader() {
        this(new MasterTaskClient());
    }

    MasterOutputArtifactUploader(MasterTaskClient taskClient) {
        this.taskClient = Objects.requireNonNull(taskClient, "taskClient is required");
    }

    @Override
    public void uploadAll(AgentExecutionContext context,
                          ClaimedExecutionPayload payload,
                          List<OutputArtifactFile> outputFiles) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(payload, "payload is required");
        List<OutputArtifactFile> files = List.copyOf(Objects.requireNonNull(outputFiles, "outputFiles is required"));

        for (OutputArtifactFile outputFile : files) {
            taskClient.uploadExecutionOutputArtifact(
                    context.masterBaseUrl(),
                    context.workerId(),
                    payload.executionId(),
                    context.apiKey(),
                    payload.leaseToken(),
                    outputFile.file(),
                    outputFile.relativePath()
            );
        }
    }
}
