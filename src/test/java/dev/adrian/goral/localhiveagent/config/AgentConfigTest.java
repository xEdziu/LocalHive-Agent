package dev.adrian.goral.localhiveagent.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigTest {

    @Test
    void shouldCreateEmptyConfig() {
        AgentConfig config = AgentConfig.empty();

        assertEquals("", config.masterBaseUrl());
        assertNull(config.workerId());
        assertEquals(0, config.sharedRamMb());
        assertFalse(config.pauseEnabled());
        assertEquals(DockerPolicy.defaultPolicy(), config.docker());
    }

    @Test
    void shouldDetectMasterBaseUrl() {
        assertFalse(AgentConfig.empty().hasMasterBaseUrl());
        assertFalse(AgentConfig.empty().withMasterBaseUrl("   ").hasMasterBaseUrl());
        assertTrue(AgentConfig.empty().withMasterBaseUrl("http://localhost:8080").hasMasterBaseUrl());
    }

    @Test
    void shouldDetectWorkerId() {
        UUID workerId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertFalse(AgentConfig.empty().hasWorkerId());
        assertTrue(AgentConfig.empty().withWorkerId(workerId).hasWorkerId());
    }

    @Test
    void shouldNormalizeMasterBaseUrl() {
        AgentConfig config = AgentConfig.empty().withMasterBaseUrl(" http://localhost:8080 ");

        assertEquals("http://localhost:8080", config.masterBaseUrl());
    }

    @Test
    void shouldNormalizeNullMasterBaseUrlToEmptyString() {
        AgentConfig config = AgentConfig.empty().withMasterBaseUrl(null);

        assertEquals("", config.masterBaseUrl());
    }

    @Test
    void shouldUpdateWorkerId() {
        UUID workerId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        AgentConfig config = AgentConfig.empty().withWorkerId(workerId);

        assertEquals(workerId, config.workerId());
    }

    @Test
    void shouldUpdateSharedRam() {
        AgentConfig config = AgentConfig.empty().withSharedRamMb(8192);

        assertEquals(8192, config.sharedRamMb());
    }

    @Test
    void shouldRejectNegativeSharedRam() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentConfig.empty().withSharedRamMb(-1)
        );

        assertTrue(exception.getMessage().contains("negative"));
    }

    @Test
    void shouldUpdatePauseEnabled() {
        AgentConfig config = AgentConfig.empty().withPauseEnabled(true);

        assertTrue(config.pauseEnabled());
    }

    @Test
    void shouldUpdateDockerPolicy() {
        DockerPolicy policy = new DockerPolicy(false, List.of("localhive/test-runner:1"), 512, 2, false);

        AgentConfig config = AgentConfig.empty().withDocker(policy);

        assertEquals(policy, config.docker());
    }

    @Test
    void shouldRejectBlankAllowedDockerImage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DockerPolicy(true, List.of("   "), 512, 2, false)
        );

        assertTrue(exception.getMessage().contains("allowedImages"));
    }

    @Test
    void shouldRejectTooLowDockerPolicyMemoryLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DockerPolicy(true, List.of("alpine:3.20"), 15, 2, false)
        );

        assertTrue(exception.getMessage().contains("maxMemoryMb"));
    }

    @Test
    void shouldRejectTooLowDockerPolicyCpuLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DockerPolicy(true, List.of("alpine:3.20"), 512, 0, false)
        );

        assertTrue(exception.getMessage().contains("maxCpuCores"));
    }
}
