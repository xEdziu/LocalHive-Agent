package dev.adrian.goral.localhiveagent.task;

public class WorkspaceUnpackException extends RuntimeException {

    public WorkspaceUnpackException(String message) {
        super(message);
    }

    public WorkspaceUnpackException(String message, Throwable cause) {
        super(message, cause);
    }
}
