package dev.adrian.goral.localhiveagent.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsecureFileCredentialStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldReturnEmptyWhenCredentialFileIsMissing() {
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(tempDir.resolve("api-key"));

        assertEquals(Optional.empty(), credentialStore.loadApiKey());
    }

    @Test
    void shouldSaveAndLoadApiKey() {
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(tempDir.resolve("api-key"));

        credentialStore.saveApiKey("test-api-key");

        assertEquals(Optional.of("test-api-key"), credentialStore.loadApiKey());
    }

    @Test
    void shouldTrimSavedApiKey() {
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(tempDir.resolve("api-key"));

        credentialStore.saveApiKey("  test-api-key  ");

        assertEquals(Optional.of("test-api-key"), credentialStore.loadApiKey());
    }

    @Test
    void shouldRejectBlankApiKey() {
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(tempDir.resolve("api-key"));

        CredentialException exception = assertThrows(
                CredentialException.class,
                () -> credentialStore.saveApiKey("   ")
        );

        assertTrue(exception.getMessage().contains("blank"));
    }

    @Test
    void shouldOverwriteExistingApiKey() {
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(tempDir.resolve("api-key"));

        credentialStore.saveApiKey("old-key");
        credentialStore.saveApiKey("new-key");

        assertEquals(Optional.of("new-key"), credentialStore.loadApiKey());
    }

    @Test
    void shouldDeleteExistingApiKey() {
        Path credentialPath = tempDir.resolve("api-key");
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(credentialPath);

        credentialStore.saveApiKey("test-api-key");
        credentialStore.deleteApiKey();

        assertFalse(Files.exists(credentialPath));
        assertEquals(Optional.empty(), credentialStore.loadApiKey());
    }

    @Test
    void shouldIgnoreDeleteWhenApiKeyIsMissing() {
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(tempDir.resolve("api-key"));

        assertDoesNotThrow(credentialStore::deleteApiKey);
    }

    @Test
    void shouldCreateParentDirectoryAutomatically() {
        Path credentialPath = tempDir.resolve("nested").resolve("credentials").resolve("api-key");
        InsecureFileCredentialStore credentialStore = new InsecureFileCredentialStore(credentialPath);

        credentialStore.saveApiKey("test-api-key");

        assertTrue(Files.exists(credentialPath));
        assertEquals(Optional.of("test-api-key"), credentialStore.loadApiKey());
    }
}
