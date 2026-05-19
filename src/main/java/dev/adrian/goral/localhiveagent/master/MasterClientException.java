package dev.adrian.goral.localhiveagent.master;

public class MasterClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public MasterClientException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = "";
    }

    public MasterClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = "";
    }

    public MasterClientException(String message, int statusCode, String responseBody) {
        super(message + " HTTP status: " + statusCode + ". Response body: " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}