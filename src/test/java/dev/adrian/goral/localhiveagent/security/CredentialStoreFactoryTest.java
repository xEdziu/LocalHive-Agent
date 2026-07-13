package dev.adrian.goral.localhiveagent.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialStoreFactoryTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldSelectWindowsDpapiCredentialStoreForWindows() {
        CredentialStore credentialStore = CredentialStoreFactory.create(tempDir, "Windows 11", false);

        assertInstanceOf(WindowsDpapiCredentialStore.class, credentialStore);
        assertEquals("Windows DPAPI", credentialStore.backendName());
        assertTrue(credentialStore.isSecure());
    }

    @Test
    void shouldSelectLinuxSecretServiceWhenAvailable() {
        CredentialStore credentialStore = CredentialStoreFactory.create(tempDir, "Linux", true);

        assertInstanceOf(LinuxSecretServiceCredentialStore.class, credentialStore);
        assertEquals("Linux Secret Service", credentialStore.backendName());
        assertTrue(credentialStore.isSecure());
    }

    @Test
    void shouldSelectInsecureFileStoreForLinuxWithoutSecretService() {
        CredentialStore credentialStore = CredentialStoreFactory.create(tempDir, "Linux", false);

        assertInstanceOf(InsecureFileCredentialStore.class, credentialStore);
        assertEquals("Insecure file storage", credentialStore.backendName());
        assertFalse(credentialStore.isSecure());
    }

    @Test
    void shouldSelectMacOsKeychainWhenAvailable() {
        CredentialStore credentialStore = CredentialStoreFactory.create(
                tempDir,
                new CredentialStoreEnvironment("Mac OS X", false, true)
        );

        assertInstanceOf(MacOsKeychainCredentialStore.class, credentialStore);
        assertEquals("macOS Keychain", credentialStore.backendName());
        assertTrue(credentialStore.isSecure());
    }

    @Test
    void shouldSelectInsecureFileStoreForMacOsWithoutUsableKeychain() {
        CredentialStore credentialStore = CredentialStoreFactory.create(
                tempDir,
                new CredentialStoreEnvironment("Mac OS X", false, false)
        );

        assertInstanceOf(InsecureFileCredentialStore.class, credentialStore);
        assertEquals("Insecure file storage", credentialStore.backendName());
        assertFalse(credentialStore.isSecure());
    }

    @Test
    void shouldSelectInsecureFileStoreForUnknownOperatingSystem() {
        CredentialStore credentialStore = CredentialStoreFactory.create(tempDir, "Plan 9", false);

        assertInstanceOf(InsecureFileCredentialStore.class, credentialStore);
        assertEquals("Insecure file storage", credentialStore.backendName());
        assertFalse(credentialStore.isSecure());
    }

    @Test
    void shouldHandleNullOperatingSystemAsUnknown() {
        CredentialStore credentialStore = CredentialStoreFactory.create(tempDir, null, false);

        assertInstanceOf(InsecureFileCredentialStore.class, credentialStore);
        assertFalse(credentialStore.isSecure());
    }
}
