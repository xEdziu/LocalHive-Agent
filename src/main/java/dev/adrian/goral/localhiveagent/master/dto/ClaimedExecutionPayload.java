package dev.adrian.goral.localhiveagent.master.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ClaimedExecutionPayload(
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

    public LocalDateTime leaseExpiresAtDateTime() {
        return MasterTimestampParser.parse(leaseExpiresAt, "leaseExpiresAt");
    }

    @Override
    public String toString() {
        return "ClaimedExecutionPayload["
                + "executionId=" + executionId
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
}
