package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class NoOpAgentExecutor implements AgentExecutor {

    public static final String FAILURE_CODE = "AGENT_NO_OP_FAILED";

    private static final Logger log = LoggerFactory.getLogger(NoOpAgentExecutor.class);

    @Override
    public AgentExecutionResult execute(ClaimedExecutionPayload payload, AgentExecutionContext context) {
        try {
            readMessage(payload.configuration());
            log.debug("NO_OP executor completed. executionId={}", payload.executionId());
            return AgentExecutionResult.succeeded();
        } catch (RuntimeException exception) {
            return AgentExecutionResult.failed(FAILURE_CODE, exception.getMessage());
        }
    }

    private static String readMessage(Map<String, Object> configuration) {
        if (configuration == null) {
            return "";
        }

        Object message = configuration.get("message");
        return message instanceof String text ? text.trim() : "";
    }
}
