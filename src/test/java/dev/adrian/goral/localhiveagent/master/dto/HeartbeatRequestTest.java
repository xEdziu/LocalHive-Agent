package dev.adrian.goral.localhiveagent.master.dto;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.heartbeat.AgentCapabilityReporter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatRequestTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void shouldSerializeCapabilitiesWithoutSecretsOrLocalConfig() throws Exception {
        HeartbeatRequest request = new HeartbeatRequest(
                false,
                4096,
                AgentCapabilityReporter.currentCapabilities(new DockerPolicy(
                        true,
                        List.of("alpine:3.20"),
                        4096,
                        8,
                        false
                ))
        );

        String json = JSON_MAPPER.writeValueAsString(request);

        assertTrue(json.contains("\"pauseEnabled\":false"));
        assertTrue(json.contains("\"sharedRamMb\":4096"));
        assertTrue(json.contains("\"capabilities\""));
        assertTrue(json.contains("\"executorId\":\"localhive.no-op\""));
        assertTrue(json.contains("\"executorId\":\"localhive.docker.workload\""));
        assertTrue(json.contains("\"gpuAllowed\":false"));
        assertFalse(json.contains("apiKey"));
        assertFalse(json.contains("masterBaseUrl"));
        assertFalse(json.contains("workerId"));
        assertFalse(json.contains("leaseToken"));
        assertFalse(json.contains("configPath"));
        assertFalse(json.contains("taskHistory"));
    }
}
