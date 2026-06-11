package dev.adrian.goral.localhiveagent.security;

import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;

public class WindowsDpapiCredentialStore implements CredentialStore {

    private final Path credentialPath;

    public WindowsDpapiCredentialStore(Path credentialPath) {
        this.credentialPath = credentialPath;
    }

    @Override
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CredentialException("API key cannot be blank.");
        }

        try {
            ensureDirectoryExists();

            byte[] plainBytes = apiKey.trim().getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBytes = Crypt32Util.cryptProtectData(plainBytes);
            String encodedValue = Base64.getEncoder().encodeToString(encryptedBytes);

            Path tempPath = credentialPath.resolveSibling(credentialPath.getFileName() + ".tmp");
            Files.writeString(tempPath, encodedValue, StandardCharsets.UTF_8);

            Files.move(
                    tempPath,
                    credentialPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException | RuntimeException exception) {
            throw new CredentialException("Failed to save API key.", exception);
        }
    }

    @Override
    public Optional<String> loadApiKey() {
        if (Files.notExists(credentialPath)) {
            return Optional.empty();
        }

        try {
            String encodedValue = Files.readString(credentialPath, StandardCharsets.UTF_8).trim();

            if (encodedValue.isBlank()) {
                return Optional.empty();
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(encodedValue);
            byte[] plainBytes = Crypt32Util.cryptUnprotectData(encryptedBytes);
            String apiKey = new String(plainBytes, StandardCharsets.UTF_8).trim();

            return apiKey.isBlank()
                    ? Optional.empty()
                    : Optional.of(apiKey);
        } catch (IOException | RuntimeException exception) {
            throw new CredentialException("Failed to load API key.", exception);
        }
    }

    @Override
    public void deleteApiKey() {
        try {
            Files.deleteIfExists(credentialPath);
        } catch (IOException exception) {
            throw new CredentialException("Failed to delete API key.", exception);
        }
    }

    private void ensureDirectoryExists() throws IOException {
        Path parent = credentialPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}