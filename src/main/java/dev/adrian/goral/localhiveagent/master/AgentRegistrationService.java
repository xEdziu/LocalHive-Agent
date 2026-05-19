package dev.adrian.goral.localhiveagent.master;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.master.dto.WorkerRegistrationRequest;
import dev.adrian.goral.localhiveagent.master.dto.WorkerRegistrationResponse;
import dev.adrian.goral.localhiveagent.system.MachineSpec;
import dev.adrian.goral.localhiveagent.system.SystemInfoProvider;

public class AgentRegistrationService {

    private final ConfigService configService;
    private final SystemInfoProvider systemInfoProvider;
    private final RegistrationClient registrationClient;

    public AgentRegistrationService(
            ConfigService configService,
            SystemInfoProvider systemInfoProvider,
            RegistrationClient registrationClient
    ) {
        this.configService = configService;
        this.systemInfoProvider = systemInfoProvider;
        this.registrationClient = registrationClient;
    }

    public AgentRegistrationResult registerCurrentMachine() {
        AgentConfig config = configService.loadOrCreate();
        validateConfigBeforeRegistration(config);

        MachineSpec machineSpec = systemInfoProvider.collectMachineSpec(config.sharedRamMb());
        WorkerRegistrationRequest request = WorkerRegistrationRequest.fromMachineSpec(machineSpec);

        WorkerRegistrationResponse response = registrationClient.register(config.masterBaseUrl(), request);

        AgentConfig updatedConfig = configService.updateWorkerId(response.workerId());

        return new AgentRegistrationResult(updatedConfig, machineSpec, response);
    }

    private static void validateConfigBeforeRegistration(AgentConfig config) {
        if (!config.hasMasterBaseUrl()) {
            throw new IllegalStateException("Master base URL is required before registration.");
        }

        if (config.hasWorkerId()) {
            throw new IllegalStateException("Worker is already registered.");
        }
    }
}