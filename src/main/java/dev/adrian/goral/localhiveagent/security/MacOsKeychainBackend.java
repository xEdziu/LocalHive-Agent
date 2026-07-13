package dev.adrian.goral.localhiveagent.security;

import java.util.Optional;

interface MacOsKeychainBackend {

    boolean isAvailable();

    Optional<String> loadApiKey(String serviceName, String accountName);

    void saveApiKey(String serviceName, String accountName, String apiKey);

    void deleteApiKey(String serviceName, String accountName);
}
