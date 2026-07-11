package dev.adrian.goral.localhiveagent.security;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class LinuxSecretServiceCredentialStore implements CredentialStore {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    private static final String LABEL = "LocalHive Agent API Key";

    private static final String APPLICATION_ATTRIBUTE = "application";
    private static final String APPLICATION_VALUE = "localhive-agent";

    private static final String CREDENTIAL_ATTRIBUTE = "credential";
    private static final String CREDENTIAL_VALUE = "api-key";

    public boolean isAvailable() {
        if (!isSecretToolInstalled()) {
            return false;
        }

        try {
            CommandResult result = SecurityCommandRunner.execute(
                    List.of(
                            "secret-tool",
                            "lookup",
                            APPLICATION_ATTRIBUTE,
                            APPLICATION_VALUE,
                            CREDENTIAL_ATTRIBUTE,
                            "availability-probe"
                    ),
                    null,
                    Duration.ofSeconds(3)
            );

            return result.exitCode() == 0
                    || result.standardError().isBlank();
        } catch (CredentialException exception) {
            return false;
        }
    }

    private static boolean isSecretToolInstalled() {
        String pathVariable = System.getenv("PATH");

        if (pathVariable == null || pathVariable.isBlank()) {
            return false;
        }

        return Arrays.stream(pathVariable.split(File.pathSeparator))
                .map(Path::of)
                .map(path -> path.resolve("secret-tool"))
                .anyMatch(path ->
                        Files.isRegularFile(path)
                                && Files.isExecutable(path)
                );
    }

    @Override
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CredentialException("API key cannot be blank.");
        }

        CommandResult result = SecurityCommandRunner.execute(
                List.of(
                        "secret-tool",
                        "store",
                        "--label=" + LABEL,
                        APPLICATION_ATTRIBUTE,
                        APPLICATION_VALUE,
                        CREDENTIAL_ATTRIBUTE,
                        CREDENTIAL_VALUE
                ),
                apiKey.trim(),
                COMMAND_TIMEOUT
        );

        ensureSuccessful(
                result,
                "Failed to save API key in Linux Secret Service."
        );
    }

    @Override
    public Optional<String> loadApiKey() {
        CommandResult result = SecurityCommandRunner.execute(
                List.of(
                        "secret-tool",
                        "lookup",
                        APPLICATION_ATTRIBUTE,
                        APPLICATION_VALUE,
                        CREDENTIAL_ATTRIBUTE,
                        CREDENTIAL_VALUE
                ),
                null,
                COMMAND_TIMEOUT
        );

        if (result.exitCode() != 0) {
            if (result.standardOutput().isBlank()) {
                return Optional.empty();
            }

            throw createCommandException(
                    "Failed to load API key from Linux Secret Service.",
                    result
            );
        }

        String apiKey = result.standardOutput().strip();

        return apiKey.isBlank()
                ? Optional.empty()
                : Optional.of(apiKey);
    }

    @Override
    public void deleteApiKey() {
        CommandResult result = SecurityCommandRunner.execute(
                List.of(
                        "secret-tool",
                        "clear",
                        APPLICATION_ATTRIBUTE,
                        APPLICATION_VALUE,
                        CREDENTIAL_ATTRIBUTE,
                        CREDENTIAL_VALUE
                ),
                null,
                COMMAND_TIMEOUT
        );

        if (result.exitCode() != 0 && !result.standardError().isBlank()) {
            throw createCommandException(
                    "Failed to delete API key from Linux Secret Service.",
                    result
            );
        }
    }

    @Override
    public String backendName() {
        return "Linux Secret Service";
    }

    private static void ensureSuccessful(
            CommandResult result,
            String message
    ) {
        if (result.exitCode() == 0) {
            return;
        }

        throw createCommandException(message, result);
    }

    private static CredentialException createCommandException(
            String message,
            CommandResult result
    ) {
        String commandError = result.standardError().strip();

        if (commandError.isBlank()) {
            return new CredentialException(message);
        }

        return new CredentialException(
                message + " Details: " + commandError
        );
    }
}