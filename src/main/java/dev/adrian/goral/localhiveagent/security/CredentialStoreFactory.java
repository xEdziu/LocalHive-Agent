package dev.adrian.goral.localhiveagent.security;

import java.nio.file.Path;
import java.util.Locale;

public final class CredentialStoreFactory {

    private CredentialStoreFactory() {
    }

    public static CredentialStore createDefault(Path configDirectory) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (osName.contains("win")) {
            return new WindowsDpapiCredentialStore(configDirectory.resolve("api-key.dpapi"));
        }

        if (osName.contains("mac")) {
            return new InsecureFileCredentialStore(configDirectory.resolve("api-key.insecure"));
        }

        if (osName.contains("nux") || osName.contains("nix") || osName.contains("aix")) {
            return new InsecureFileCredentialStore(configDirectory.resolve("api-key.insecure"));
        }

        return new InsecureFileCredentialStore(configDirectory.resolve("api-key.insecure"));
    }

    public static boolean isUsingInsecureFallback() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return !osName.contains("win");
    }
}