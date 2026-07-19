package dev.adrian.goral.localhiveagent.task;

public class OutputDirectoryInvalidException extends RuntimeException {

    public OutputDirectoryInvalidException(String message) {
        super(message);
    }

    public OutputDirectoryInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
