package dev.adrian.goral.localhiveagent.security;

import java.util.Optional;

public interface CredentialStore {

    void saveApiKey(String apiKey);

    Optional<String> loadApiKey();

    void deleteApiKey();

    default boolean hasApiKey() {
        return loadApiKey().isPresent();
    }
}