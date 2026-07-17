package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;

public interface AgentExecutor {

    AgentExecutionResult execute(ClaimedExecutionPayload payload, AgentExecutionContext context);
}
