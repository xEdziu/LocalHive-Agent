package dev.adrian.goral.localhiveagent.master;

public class MasterClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;
    private final String userMessage;

    public MasterClientException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = "";
        this.userMessage = message;
    }

    public MasterClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = "";
        this.userMessage = message;
    }

    public MasterClientException(String message, int statusCode, String responseBody) {
        super(message + " HTTP status: " + statusCode + ". Response body: " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
        this.userMessage = message;
    }

    public MasterClientException(String technicalMessage, String userMessage, int statusCode, String responseBody) {
        super(technicalMessage + " HTTP status: " + statusCode + ". Response body: " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
        this.userMessage = userMessage == null || userMessage.isBlank()
                ? technicalMessage
                : userMessage;
    }

    public MasterClientException(String technicalMessage, String userMessage, Throwable cause) {
        super(technicalMessage, cause);
        this.statusCode = -1;
        this.responseBody = "";
        this.userMessage = userMessage == null || userMessage.isBlank()
                ? technicalMessage
                : userMessage;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }

    public String userMessage() {
        return userMessage;
    }

    @Override
    public String getMessage() {
        return userMessage;
    }
}