package dev.adrian.goral.localhiveagent.security;

import java.nio.file.Path;
import java.util.Locale;

public final class CredentialStoreFactory {

    private CredentialStoreFactory() {
    }

    public static CredentialStore createDefault(Path configDirectory) {
        String operatingSystem = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        return create(configDirectory, new CredentialStoreEnvironment(
                operatingSystem,
                isLinux(operatingSystem) && new LinuxSecretServiceCredentialStore().isAvailable(),
                isMacOs(operatingSystem) && new MacOsKeychainCredentialStore().isAvailable()
        ));
    }

    static CredentialStore create(
            Path configDirectory,
            String operatingSystem,
            boolean linuxSecretServiceAvailable
    ) {
        return create(configDirectory, new CredentialStoreEnvironment(
                operatingSystem,
                linuxSecretServiceAvailable,
                false
        ));
    }

    static CredentialStore create(
            Path configDirectory,
            CredentialStoreEnvironment environment
    ) {
        String normalizedOperatingSystem = environment.operatingSystem() == null
                ? ""
                : environment.operatingSystem().toLowerCase(Locale.ROOT);

        if (normalizedOperatingSystem.contains("win")) {
            return new WindowsDpapiCredentialStore(
                    configDirectory.resolve("api-key.dpapi")
            );
        }

        if (isLinux(normalizedOperatingSystem)) {
            return createLinuxCredentialStore(configDirectory, environment.linuxSecretServiceAvailable());
        }

        if (isMacOs(normalizedOperatingSystem)) {
            return createMacOsCredentialStore(configDirectory, environment.macOsKeychainAvailable());
        }

        return new InsecureFileCredentialStore(
                configDirectory.resolve("api-key.insecure")
        );
    }

    private static CredentialStore createLinuxCredentialStore(
            Path configDirectory,
            boolean linuxSecretServiceAvailable
    ) {
        if (linuxSecretServiceAvailable) {
            return new LinuxSecretServiceCredentialStore();
        }

        return new InsecureFileCredentialStore(
                configDirectory.resolve("api-key.insecure")
        );
    }

    private static CredentialStore createMacOsCredentialStore(
            Path configDirectory,
            boolean macOsKeychainAvailable
    ) {
        if (macOsKeychainAvailable) {
            return new MacOsKeychainCredentialStore();
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

    private static boolean isMacOs(String operatingSystem) {
        return operatingSystem.contains("mac");
    }
}
