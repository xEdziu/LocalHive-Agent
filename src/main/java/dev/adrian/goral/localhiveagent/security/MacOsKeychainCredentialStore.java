package dev.adrian.goral.localhiveagent.security;

import java.util.Objects;
import java.util.Optional;

public final class MacOsKeychainCredentialStore implements CredentialStore {

    static final String SERVICE_NAME = "dev.adrian.goral.localhiveagent.api-key";
    static final String ACCOUNT_NAME = "worker-api-key";

    private final MacOsKeychainBackend backend;

    public MacOsKeychainCredentialStore() {
        this(new NativeMacOsKeychainBackend());
    }

    MacOsKeychainCredentialStore(MacOsKeychainBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend is required");
    }

    public boolean isAvailable() {
        try {
            return backend.isAvailable();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CredentialException("API key cannot be blank.");
        }

        try {
            backend.saveApiKey(SERVICE_NAME, ACCOUNT_NAME, apiKey.trim());
        } catch (CredentialException exception) {
            throw new CredentialException("Unable to save API key in macOS Keychain.");
        } catch (RuntimeException exception) {
            throw new CredentialException("Unable to save API key in macOS Keychain.");
        }
    }

    @Override
    public Optional<String> loadApiKey() {
        try {
            return backend.loadApiKey(SERVICE_NAME, ACCOUNT_NAME)
                    .map(String::trim)
                    .filter(value -> !value.isBlank());
        } catch (CredentialException exception) {
            throw new CredentialException("Unable to read API key from macOS Keychain.");
        } catch (RuntimeException exception) {
            throw new CredentialException("Unable to read API key from macOS Keychain.");
        }
    }

    @Override
    public void deleteApiKey() {
        try {
            backend.deleteApiKey(SERVICE_NAME, ACCOUNT_NAME);
        } catch (CredentialException exception) {
            throw new CredentialException("Unable to delete API key from macOS Keychain.");
        } catch (RuntimeException exception) {
            throw new CredentialException("Unable to delete API key from macOS Keychain.");
        }
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public String backendName() {
        return "macOS Keychain";
    }
}
