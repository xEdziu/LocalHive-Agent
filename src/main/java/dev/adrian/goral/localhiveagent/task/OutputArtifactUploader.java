package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

import java.util.List;

@FunctionalInterface
interface OutputArtifactUploader {

    void uploadAll(AgentExecutionContext context,
                   ClaimedExecutionPayload payload,
                   List<OutputArtifactFile> outputFiles);
}
