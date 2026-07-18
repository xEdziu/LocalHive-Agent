package dev.adrian.goral.localhiveagent.task;

public class WorkspacePackageInvalidException extends RuntimeException {

    public WorkspacePackageInvalidException(String message) {
        super(message);
    }

    public WorkspacePackageInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
