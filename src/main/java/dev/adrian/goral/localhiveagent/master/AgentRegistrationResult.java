package dev.adrian.goral.localhiveagent.master;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.master.dto.WorkerRegistrationResponse;
import dev.adrian.goral.localhiveagent.system.MachineSpec;

public record AgentRegistrationResult(
        AgentConfig updatedConfig,
        MachineSpec machineSpec,
        WorkerRegistrationResponse response
) {
}