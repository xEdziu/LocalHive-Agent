package dev.adrian.goral.localhiveagent.validation;

import dev.adrian.goral.localhiveagent.config.AgentConfig;

public final class AgentConfigValidator {

    private AgentConfigValidator() {
    }

    public static void validateWorkerApiReady(AgentConfig config, boolean hasApiKey) {
        if (config == null) {
            throw new IllegalStateException("Agent config is required.");
        }

        if (!config.hasMasterBaseUrl()) {
            throw new IllegalStateException("Master base URL is required.");
        }

        if (!config.hasWorkerId()) {
            throw new IllegalStateException("Worker ID is required.");
        }

        if (!hasApiKey) {
            throw new IllegalStateException("API key is required.");
        }
    }

    public static int parseSharedRamMb(String value, int totalRamMb) {
        if (totalRamMb < 1) {
            throw new IllegalArgumentException("Total RAM must be positive.");
        }

        if (value == null || value.isBlank()) {
            return 0;
        }

        int sharedRamMb = parseInteger(value);

        if (sharedRamMb < 0) {
            throw new IllegalArgumentException("Shared RAM cannot be negative.");
        }

        if (sharedRamMb > totalRamMb) {
            throw new IllegalArgumentException("Shared RAM cannot be greater than total RAM.");
        }

        return sharedRamMb;
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Shared RAM must be a valid integer.", exception);
        }
    }
}