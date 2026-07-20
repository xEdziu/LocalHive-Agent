package dev.adrian.goral.localhiveagent.heartbeat;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.master.dto.AgentCapabilities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityReporterTest {

    @Test
    void shouldReportBuiltInExecutorsAndDockerPolicy() {
        AgentCapabilities capabilities = AgentCapabilityReporter.currentCapabilities(new DockerPolicy(
                true,
                List.of("alpine:3.20", "eclipse-temurin:21-jre"),
                2048,
                2,
                true
        ));

        assertEquals(2, capabilities.executors().size());
        assertEquals("localhive.no-op", capabilities.executors().get(0).executorId());
        assertEquals(1, capabilities.executors().get(0).executorContractVersion());
        assertTrue(capabilities.executors().get(0).enabled());
        assertEquals("localhive.docker.workload", capabilities.executors().get(1).executorId());
        assertEquals(1, capabilities.executors().get(1).executorContractVersion());
        assertTrue(capabilities.executors().get(1).enabled());
        assertTrue(capabilities.docker().enabled());
        assertEquals(List.of("alpine:3.20", "eclipse-temurin:21-jre"), capabilities.docker().allowedImages());
        assertEquals(2048, capabilities.docker().maxMemoryMb());
        assertEquals(2, capabilities.docker().maxCpuCores());
        assertTrue(capabilities.docker().gpuAllowed());
    }

    @Test
    void shouldReportDockerExecutorDisabledWhenPolicyIsDisabled() {
        AgentCapabilities capabilities = AgentCapabilityReporter.currentCapabilities(new DockerPolicy(
                false,
                List.of("alpine:3.20"),
                4096,
                8,
                false
        ));

        assertTrue(capabilities.executors().get(0).enabled());
        assertFalse(capabilities.executors().get(1).enabled());
        assertFalse(capabilities.docker().enabled());
        assertFalse(capabilities.docker().gpuAllowed());
    }
}
