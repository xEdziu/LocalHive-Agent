package dev.adrian.goral.localhiveagent.security;

import java.nio.file.Path;
import java.util.Locale;

public final class CredentialStoreFactory {

    private CredentialStoreFactory() {
    }

    public static CredentialStore createDefault(Path configDirectory) {
        String operatingSystem = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        return create(
                configDirectory,
                operatingSystem,
                () -> new LinuxSecretServiceCredentialStore().isAvailable()
        );
    }

    static CredentialStore create(
            Path configDirectory,
            String operatingSystem,
            boolean linuxSecretServiceAvailable
    ) {
        return create(
                configDirectory,
                operatingSystem,
                () -> linuxSecretServiceAvailable
        );
    }

    private static CredentialStore create(
            Path configDirectory,
            String operatingSystem,
            LinuxSecretServiceAvailability linuxSecretServiceAvailability
    ) {
        String normalizedOperatingSystem = operatingSystem == null
                ? ""
                : operatingSystem.toLowerCase(Locale.ROOT);

        if (normalizedOperatingSystem.contains("win")) {
            return new WindowsDpapiCredentialStore(
                    configDirectory.resolve("api-key.dpapi")
            );
        }

        if (isLinux(normalizedOperatingSystem)) {
            return createLinuxCredentialStore(configDirectory, linuxSecretServiceAvailability);
        }

        if (normalizedOperatingSystem.contains("mac")) {
            return new InsecureFileCredentialStore(
                    configDirectory.resolve("api-key.insecure")
            );
        }

        return new InsecureFileCredentialStore(
                configDirectory.resolve("api-key.insecure")
        );
    }

    private static CredentialStore createLinuxCredentialStore(
            Path configDirectory,
            LinuxSecretServiceAvailability linuxSecretServiceAvailability
    ) {
        if (linuxSecretServiceAvailability.isAvailable()) {
            return new LinuxSecretServiceCredentialStore();
        }

        return new InsecureFileCredentialStore(
                configDirectory.resolve("api-key.insecure")
        );
    }

    private static boolean isLinux(String operatingSystem) {
        return operatingSystem.contains("linux")
                || operatingSystem.contains("nux")
                || operatingSystem.contains("nix");
    }

    @FunctionalInterface
    private interface LinuxSecretServiceAvailability {

        boolean isAvailable();
    }
}
