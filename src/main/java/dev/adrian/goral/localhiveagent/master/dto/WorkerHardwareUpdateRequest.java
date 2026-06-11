package dev.adrian.goral.localhiveagent.master.dto;

import dev.adrian.goral.localhiveagent.system.MachineSpec;

public record WorkerHardwareUpdateRequest(
        String hostname,
        String ipAddress,
        String osType,
        Integer totalRamMb,
        Integer sharedRamMb,
        Integer cpuCores,
        String gpuName
) {

    public static WorkerHardwareUpdateRequest fromMachineSpec(MachineSpec machineSpec) {
        return new WorkerHardwareUpdateRequest(
                machineSpec.hostname(),
                machineSpec.ipAddress(),
                machineSpec.osType(),
                machineSpec.totalRamMb(),
                machineSpec.sharedRamMb(),
                machineSpec.cpuCores(),
                machineSpec.gpuName() == null || machineSpec.gpuName().isBlank()
                        ? null
                        : machineSpec.gpuName()
        );
    }
}