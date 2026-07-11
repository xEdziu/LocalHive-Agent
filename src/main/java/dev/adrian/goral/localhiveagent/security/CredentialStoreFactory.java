package dev.adrian.goral.localhiveagent.security;

import java.nio.file.Path;
import java.util.Locale;

public final class CredentialStoreFactory {

    private CredentialStoreFactory() {
    }

    public static CredentialStore createDefault(Path configDirectory) {
        String operatingSystem = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        if (operatingSystem.contains("win")) {
            return new WindowsDpapiCredentialStore(
                    configDirectory.resolve("api-key.dpapi")
            );
        }

        if (isLinux(operatingSystem)) {
            return createLinuxCredentialStore(configDirectory);
        }

        if (operatingSystem.contains("mac")) {
            return new InsecureFileCredentialStore(
                    configDirectory.resolve("api-key.insecure")
            );
        }

        return new InsecureFileCredentialStore(
                configDirectory.resolve("api-key.insecure")
        );
    }

    private static CredentialStore createLinuxCredentialStore(
            Path configDirectory
    ) {
        LinuxSecretServiceCredentialStore secretServiceStore =
                new LinuxSecretServiceCredentialStore();

        if (secretServiceStore.isAvailable()) {
            return secretServiceStore;
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
}