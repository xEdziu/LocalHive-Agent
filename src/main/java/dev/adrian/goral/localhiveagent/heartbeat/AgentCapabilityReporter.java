package dev.adrian.goral.localhiveagent.heartbeat;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.master.dto.AgentCapabilities;
import dev.adrian.goral.localhiveagent.task.AgentExecutorRegistry;

import java.util.List;

public final class AgentCapabilityReporter {

    private AgentCapabilityReporter() {
    }

    public static AgentCapabilities currentCapabilities(DockerPolicy dockerPolicy) {
        DockerPolicy effectiveDockerPolicy = dockerPolicy == null
                ? DockerPolicy.defaultPolicy()
                : dockerPolicy;

        return new AgentCapabilities(
                List.of(
                        new AgentCapabilities.ExecutorCapability(
                                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                                true
                        ),
                        new AgentCapabilities.ExecutorCapability(
                                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION,
                                effectiveDockerPolicy.enabled()
                        )
                ),
                new AgentCapabilities.DockerCapability(
                        effectiveDockerPolicy.enabled(),
                        effectiveDockerPolicy.allowedImages(),
                        effectiveDockerPolicy.maxMemoryMb(),
                        effectiveDockerPolicy.maxCpuCores(),
                        effectiveDockerPolicy.allowGpu()
                )
        );
    }
}
