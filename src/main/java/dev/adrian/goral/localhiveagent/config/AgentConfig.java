package dev.adrian.goral.localhiveagent.config;

import java.util.UUID;

public record AgentConfig(
        String masterBaseUrl,
        UUID workerId,
        int sharedRamMb,
        boolean pauseEnabled
) {

    public static AgentConfig empty() {
        return new AgentConfig("", null, 0, false);
    }

    public boolean hasMasterBaseUrl() {
        return masterBaseUrl != null && !masterBaseUrl.isBlank();
    }

    public boolean hasWorkerId() {
        return workerId != null;
    }

    public AgentConfig withMasterBaseUrl(String masterBaseUrl) {
        return new AgentConfig(
                normalizeText(masterBaseUrl),
                workerId,
                sharedRamMb,
                pauseEnabled
        );
    }

    public AgentConfig withWorkerId(UUID workerId) {
        return new AgentConfig(
                masterBaseUrl,
                workerId,
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
                sharedRamMb,
                pauseEnabled
        );
    }

    public AgentConfig withPauseEnabled(boolean pauseEnabled) {
        return new AgentConfig(
                masterBaseUrl,
                workerId,
                sharedRamMb,
                pauseEnabled
        );
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}