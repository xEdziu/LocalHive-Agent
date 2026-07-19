package dev.adrian.goral.localhiveagent.master.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimedExecutionPayload(
        UUID executionId,
        String displayName,
        String executorId,
        int executorContractVersion,
        Map<String, Object> configuration,
        int requiredRamMb,
        int requiredCpuCores,
        boolean gpuRequired,
        String leaseToken,
        Object leaseExpiresAt
) {

    private static final int MAX_DISPLAY_NAME_LENGTH = 255;
    private static final String NO_OP_EXECUTOR_ID = "localhive.no-op";
    private static final String DOCKER_WORKLOAD_EXECUTOR_ID = "localhive.docker.workload";

    public ClaimedExecutionPayload(
            UUID executionId,
            String executorId,
            int executorContractVersion,
            Map<String, Object> configuration,
            int requiredRamMb,
            int requiredCpuCores,
            boolean gpuRequired,
            String leaseToken,
            Object leaseExpiresAt
    ) {
        this(
                executionId,
                null,
                executorId,
                executorContractVersion,
                configuration,
                requiredRamMb,
                requiredCpuCores,
                gpuRequired,
                leaseToken,
                leaseExpiresAt
        );
    }

    public ClaimedExecutionPayload {
        displayName = normalizeDisplayName(displayName);
    }

    public LocalDateTime leaseExpiresAtDateTime() {
        return MasterTimestampParser.parse(leaseExpiresAt, "leaseExpiresAt");
    }

    public String displayNameOrFallback() {
        if (displayName != null) {
            return displayName;
        }

        if (DOCKER_WORKLOAD_EXECUTOR_ID.equals(executorId)) {
            String dockerImage = dockerImage();
            if (dockerImage != null) {
                return "Docker workload: " + dockerImage;
            }
        }

        if (NO_OP_EXECUTOR_ID.equals(executorId)) {
            return "NO-OP smoke test";
        }

        if (executorId != null && !executorId.isBlank()) {
            return executorId.trim();
        }

        return "Execution";
    }

    @Override
    public String toString() {
        return "ClaimedExecutionPayload["
                + "executionId=" + executionId
                + ", displayName=" + displayName
                + ", executorId=" + executorId
                + ", executorContractVersion=" + executorContractVersion
                + ", configuration=" + configuration
                + ", requiredRamMb=" + requiredRamMb
                + ", requiredCpuCores=" + requiredCpuCores
                + ", gpuRequired=" + gpuRequired
                + ", leaseToken=<redacted>"
                + ", leaseExpiresAt=" + leaseExpiresAt
                + ']';
    }

    private String dockerImage() {
        if (configuration == null) {
            return null;
        }

        Object value;
        try {
            value = configuration.get("image");
        } catch (RuntimeException exception) {
            return null;
        }
        if (!(value instanceof String image)) {
            return null;
        }

        String trimmed = image.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("displayName must be at most 255 characters.");
        }

        return trimmed;
    }
}
