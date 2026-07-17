package dev.adrian.goral.localhiveagent.master.dto;

public record ExecutionFailureRequest(
        String failureCode,
        String failureMessage
) {
}
