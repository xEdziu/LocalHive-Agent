package dev.adrian.goral.localhiveagent.task;

public record AgentExecutionResult(
        boolean success,
        String failureCode,
        String failureMessage
) {

    public static AgentExecutionResult succeeded() {
        return new AgentExecutionResult(true, "", "");
    }

    public static AgentExecutionResult failed(String failureCode, String failureMessage) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode cannot be blank.");
        }

        return new AgentExecutionResult(false, failureCode, failureMessage == null ? "" : failureMessage.trim());
    }
}
