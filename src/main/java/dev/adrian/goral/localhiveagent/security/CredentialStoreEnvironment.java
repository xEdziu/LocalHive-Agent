package dev.adrian.goral.localhiveagent.security;

record CredentialStoreEnvironment(
        String operatingSystem,
        boolean linuxSecretServiceAvailable,
        boolean macOsKeychainAvailable
) {
}
