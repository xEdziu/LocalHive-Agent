package dev.adrian.goral.localhiveagent.system;

public record MachineSpec(
        String hostname,
        String ipAddress,
        String osType,
        int totalRamMb,
        int sharedRamMb,
        int cpuCores,
        String gpuName
) {
}