package dev.adrian.goral.localhiveagent.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

public class InsecureFileCredentialStore implements CredentialStore {

    private final Path credentialPath;

    public InsecureFileCredentialStore(Path credentialPath) {
        this.credentialPath = credentialPath;
    }

    @Override
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CredentialException("API key cannot be blank.");
        }

        try {
            ensureDirectoryExists();

            Path tempPath = credentialPath.resolveSibling(credentialPath.getFileName() + ".tmp");
            Files.writeString(tempPath, apiKey.trim(), StandardCharsets.UTF_8);
            restrictFilePermissionsIfSupported(tempPath);

            Files.move(
                    tempPath,
                    credentialPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

            restrictFilePermissionsIfSupported(credentialPath);
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
            String apiKey = Files.readString(credentialPath, StandardCharsets.UTF_8).trim();

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

    private static void restrictFilePermissionsIfSupported(Path path) {
        try {
            Files.setPosixFilePermissions(
                    path,
                    PosixFilePermissions.fromString("rw-------")
            );
        } catch (UnsupportedOperationException | IOException ignored) {
            // POSIX file permissions are not available on every file system.
        }
    }
}