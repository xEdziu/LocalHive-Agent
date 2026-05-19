package dev.adrian.goral.localhiveagent.master.dto;

import dev.adrian.goral.localhiveagent.system.MachineSpec;

public record WorkerRegistrationRequest(
        String hostname,
        String ipAddress,
        String osType,
        int totalRamMb,
        int sharedRamMb,
        int cpuCores,
        String gpuName
) {

    public static WorkerRegistrationRequest fromMachineSpec(MachineSpec machineSpec) {
        return new WorkerRegistrationRequest(
                machineSpec.hostname(),
                machineSpec.ipAddress(),
                machineSpec.osType(),
                machineSpec.totalRamMb(),
                machineSpec.sharedRamMb(),
                machineSpec.cpuCores(),
                machineSpec.gpuName().isBlank() ? null : machineSpec.gpuName()
        );
    }
}