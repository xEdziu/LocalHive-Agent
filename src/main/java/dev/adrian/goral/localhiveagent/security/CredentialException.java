package dev.adrian.goral.localhiveagent.security;

public class CredentialException extends RuntimeException {

    public CredentialException(String message, Throwable cause) {
        super(message, cause);
    }

    public CredentialException(String message) {
        super(message);
    }
}