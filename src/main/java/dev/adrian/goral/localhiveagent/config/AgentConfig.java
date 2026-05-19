package dev.adrian.goral.localhiveagent.config;

import java.util.UUID;

public record AgentConfig(
        String masterBaseUrl,
        UUID workerId,
        String apiKey,
        int sharedRamMb,
        boolean pauseEnabled
) {

    public static AgentConfig empty() {
        return new AgentConfig("", null, "", 0, false);
    }

    public boolean hasMasterBaseUrl() {
        return masterBaseUrl != null && !masterBaseUrl.isBlank();
    }

    public boolean hasWorkerId() {
        return workerId != null;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AgentConfig withMasterBaseUrl(String masterBaseUrl) {
        return new AgentConfig(
                normalizeText(masterBaseUrl),
                workerId,
                apiKey,
                sharedRamMb,
                pauseEnabled
        );
    }

    public AgentConfig withWorkerId(UUID workerId) {
        return new AgentConfig(
                masterBaseUrl,
                workerId,
                apiKey,
                sharedRamMb,
                pauseEnabled
        );
    }

    public AgentConfig withApiKey(String apiKey) {
        return new AgentConfig(
                masterBaseUrl,
                workerId,
                normalizeText(apiKey),
                sharedRamMb,
                pauseEnabled
        );
    }

    public AgentConfig withSharedRamMb(int sharedRamMb) {
        if (sharedRamMb < 0) {
            throw new IllegalArgumentException("Shared RAM cannot be negative.");
        }

        return new AgentConfig(
                masterBaseUrl,
                workerId,
                apiKey,
                sharedRamMb,
                pauseEnabled
        );
    }

    public AgentConfig withPauseEnabled(boolean pauseEnabled) {
        return new AgentConfig(
                masterBaseUrl,
                workerId,
                apiKey,
                sharedRamMb,
                pauseEnabled
        );
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}