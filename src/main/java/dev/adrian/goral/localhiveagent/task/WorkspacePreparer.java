package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

@FunctionalInterface
interface WorkspacePreparer {

    PreparedWorkspace prepare(AgentExecutionContext context,
                              ClaimedExecutionPayload payload,
                              DockerWorkspaceConfig workspace);
}
