package dev.adrian.goral.localhiveagent.security;

import dev.adrian.goral.localhiveagent.logging.AgentLogPolicy;
import dev.adrian.goral.localhiveagent.logging.AgentLogging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacOsKeychainCredentialStoreTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void tearDown() {
        AgentLogging.closeCurrent();
    }

    @Test
    void shouldReturnEmptyWhenCredentialIsMissing() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        assertEquals(Optional.empty(), credentialStore.loadApiKey());
    }

    @Test
    void shouldSaveAndLoadApiKey() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        credentialStore.saveApiKey("test-api-key");

        assertEquals(Optional.of("test-api-key"), credentialStore.loadApiKey());
        assertEquals(MacOsKeychainCredentialStore.SERVICE_NAME, backend.lastServiceName);
        assertEquals(MacOsKeychainCredentialStore.ACCOUNT_NAME, backend.lastAccountName);
    }

    @Test
    void shouldTrimSavedApiKey() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        credentialStore.saveApiKey("  test-api-key  ");

        assertEquals(Optional.of("test-api-key"), credentialStore.loadApiKey());
    }

    @Test
    void shouldUpdateExistingApiKey() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        credentialStore.saveApiKey("old-key");
        credentialStore.saveApiKey("new-key");

        assertEquals(Optional.of("new-key"), credentialStore.loadApiKey());
    }

    @Test
    void shouldDeleteExistingApiKey() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        credentialStore.saveApiKey("test-api-key");
        credentialStore.deleteApiKey();

        assertEquals(Optional.empty(), credentialStore.loadApiKey());
    }

    @Test
    void shouldIgnoreDeleteWhenCredentialIsMissing() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        assertDoesNotThrow(credentialStore::deleteApiKey);
    }

    @Test
    void shouldRejectBlankApiKey() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        CredentialException exception = assertThrows(
                CredentialException.class,
                () -> credentialStore.saveApiKey("   ")
        );

        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void shouldExposeSecureBackendMetadata() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        assertTrue(credentialStore.isSecure());
        assertEquals("macOS Keychain", credentialStore.backendName());
        assertTrue(credentialStore.isAvailable());
    }

    @Test
    void shouldWrapLoadFailureWithSafeMessage() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        backend.loadFailure = new CredentialException("native failure contains test-api-key");
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        CredentialException exception = assertThrows(CredentialException.class, credentialStore::loadApiKey);

        assertTrue(exception.getMessage().contains("macOS Keychain"));
        assertFalse(exception.getMessage().contains("test-api-key"));
    }

    @Test
    void shouldWrapSaveFailureWithSafeMessage() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        backend.saveFailure = new CredentialException("native failure contains test-api-key");
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        CredentialException exception = assertThrows(
                CredentialException.class,
                () -> credentialStore.saveApiKey("test-api-key")
        );

        assertTrue(exception.getMessage().contains("macOS Keychain"));
        assertFalse(exception.getMessage().contains("test-api-key"));
    }

    @Test
    void shouldWrapDeleteFailureWithSafeMessage() {
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        backend.deleteFailure = new CredentialException("native failure contains test-api-key");
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);

        CredentialException exception = assertThrows(CredentialException.class, credentialStore::deleteApiKey);

        assertTrue(exception.getMessage().contains("macOS Keychain"));
        assertFalse(exception.getMessage().contains("test-api-key"));
    }

    @Test
    void shouldNotWriteApiKeyToLogs() throws Exception {
        AgentLogPolicy policy = new AgentLogPolicy(tempDir.resolve("logs"), 1024, 3);
        FakeMacOsKeychainBackend backend = new FakeMacOsKeychainBackend();
        MacOsKeychainCredentialStore credentialStore = new MacOsKeychainCredentialStore(backend);
        String apiKey = "test-api-key-should-not-be-logged";

        AgentLogging.initialize(policy);
        credentialStore.saveApiKey(apiKey);
        credentialStore.loadApiKey();
        credentialStore.deleteApiKey();

        AgentLogging.closeCurrent();

        Path activeLogFile = policy.logDirectory().resolve("localhive-agent.0.log");
        String logContent = Files.exists(activeLogFile)
                ? Files.readString(activeLogFile, StandardCharsets.UTF_8)
                : "";

        assertFalse(logContent.contains(apiKey));
    }

    private static final class FakeMacOsKeychainBackend implements MacOsKeychainBackend {

        private boolean available = true;
        private String storedApiKey;
        private CredentialException loadFailure;
        private CredentialException saveFailure;
        private CredentialException deleteFailure;
        private String lastServiceName;
        private String lastAccountName;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public Optional<String> loadApiKey(String serviceName, String accountName) {
            lastServiceName = serviceName;
            lastAccountName = accountName;

            if (loadFailure != null) {
                throw loadFailure;
            }

            return Optional.ofNullable(storedApiKey);
        }

        @Override
        public void saveApiKey(String serviceName, String accountName, String apiKey) {
            lastServiceName = serviceName;
            lastAccountName = accountName;

            if (saveFailure != null) {
                throw saveFailure;
            }

            storedApiKey = apiKey;
        }

        @Override
        public void deleteApiKey(String serviceName, String accountName) {
            lastServiceName = serviceName;
            lastAccountName = accountName;

            if (deleteFailure != null) {
                throw deleteFailure;
            }

            storedApiKey = null;
        }
    }
}
