package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerWorkloadConfigParserTest {

    private static final UUID WORKSPACE_ARTIFACT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DockerWorkloadConfigParser parser = new DockerWorkloadConfigParser();

    @Test
    void shouldParseValidConfig() {
        DockerWorkloadConfig config = parser.parse(validConfig());

        assertEquals("alpine:3.20", config.image());
        assertEquals(List.of("sh", "-c", "echo LocalHive Docker workload"), config.command());
        assertEquals(30, config.timeoutSeconds());
        assertEquals(128, config.memoryMb());
        assertEquals(1, config.cpuCores());
        assertFalse(config.gpuRequired());
        assertNull(config.workspace());
    }

    @Test
    void shouldParseValidConfigWithWorkspace() {
        DockerWorkloadConfig config = parser.parse(validConfig("workspace", validWorkspaceConfig()));

        assertEquals(WORKSPACE_ARTIFACT_ID, config.workspace().artifactId());
        assertEquals("/workspace", config.workspace().mountPath());
        assertTrue(config.workspace().readOnly());
    }

    @Test
    void shouldRejectNonAllowlistedImage() {
        Map<String, Object> config = validConfig("image", "ubuntu:24.04");

        assertThrows(DockerImageNotAllowedException.class, () -> parser.parse(config));
    }

    @Test
    void shouldAcceptImageAllowedByConfiguredPolicy() {
        DockerPolicy policy = new DockerPolicy(true, List.of("localhive/test-runner:1"), 512, 2, false);
        Map<String, Object> config = validConfig("image", "localhive/test-runner:1");

        DockerWorkloadConfig parsed = parser.parse(config, policy);

        assertEquals("localhive/test-runner:1", parsed.image());
    }

    @Test
    void shouldRejectDefaultImageWhenAllowedImagesIsEmpty() {
        DockerPolicy policy = new DockerPolicy(true, List.of(), 512, 2, false);

        assertThrows(DockerImageNotAllowedException.class, () -> parser.parse(validConfig(), policy));
    }

    @Test
    void shouldRejectGpuRequired() {
        Map<String, Object> config = validConfig("gpu", Map.of("required", true));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectEmptyCommand() {
        Map<String, Object> config = validConfig("command", List.of());

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectBlankCommandElement() {
        Map<String, Object> config = validConfig("command", List.of("sh", "   "));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectWorkspaceWithoutArtifactId() {
        Map<String, Object> workspace = new HashMap<>(validWorkspaceConfig());
        workspace.remove("artifactId");
        Map<String, Object> config = validConfig("workspace", workspace);

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectWorkspaceArtifactIdThatIsNotUuid() {
        Map<String, Object> workspace = new HashMap<>(validWorkspaceConfig());
        workspace.put("artifactId", "not-a-uuid");
        Map<String, Object> config = validConfig("workspace", workspace);

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectWorkspaceMountPathOtherThanWorkspace() {
        Map<String, Object> workspace = new HashMap<>(validWorkspaceConfig());
        workspace.put("mountPath", "/data");
        Map<String, Object> config = validConfig("workspace", workspace);

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectWritableWorkspaceMount() {
        Map<String, Object> workspace = new HashMap<>(validWorkspaceConfig());
        workspace.put("readOnly", false);
        Map<String, Object> config = validConfig("workspace", workspace);

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectInvalidTimeout() {
        Map<String, Object> config = validConfig("timeoutSeconds", 301);

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldAcceptDefaultPolicyMaximumResources() {
        Map<String, Object> config = validConfig("resources", Map.of("memoryMb", 4096, "cpuCores", 8));

        DockerWorkloadConfig parsed = parser.parse(config);

        assertEquals(4096, parsed.memoryMb());
        assertEquals(8, parsed.cpuCores());
    }

    @Test
    void shouldRejectInvalidMemory() {
        Map<String, Object> config = validConfig("resources", Map.of("memoryMb", 15, "cpuCores", 1));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectMemoryAboveDefaultPolicyLimit() {
        Map<String, Object> config = validConfig("resources", Map.of("memoryMb", 4097, "cpuCores", 1));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectMemoryAboveConfiguredPolicyLimit() {
        DockerPolicy policy = new DockerPolicy(true, List.of("alpine:3.20"), 127, 8, false);

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(validConfig(), policy));
    }

    @Test
    void shouldRejectInvalidCpu() {
        Map<String, Object> config = validConfig("resources", Map.of("memoryMb", 128, "cpuCores", 9));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectCpuAboveConfiguredPolicyLimit() {
        DockerPolicy policy = new DockerPolicy(true, List.of("alpine:3.20"), 4096, 1, false);
        Map<String, Object> config = validConfig("resources", Map.of("memoryMb", 128, "cpuCores", 2));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config, policy));
    }

    @Test
    void shouldStillRejectGpuWhenPolicyAllowsGpu() {
        DockerPolicy policy = new DockerPolicy(true, List.of("alpine:3.20"), 4096, 8, true);
        Map<String, Object> config = validConfig("gpu", Map.of("required", true));

        DockerWorkloadConfigurationException exception = assertThrows(
                DockerWorkloadConfigurationException.class,
                () -> parser.parse(config, policy)
        );

        assertTrue(exception.getMessage().contains("not implemented yet"));
    }

    @Test
    void shouldNotExposeCommandInConfigToString() {
        DockerWorkloadConfig config = parser.parse(validConfig("command", List.of("sh", "-c", "echo secret-token")));

        String text = config.toString();

        assertFalse(text.contains("secret-token"));
        assertTrue(text.contains("commandSize=3"));
    }

    private static Map<String, Object> validConfig() {
        return Map.of(
                "image", "alpine:3.20",
                "command", List.of("sh", "-c", "echo LocalHive Docker workload"),
                "timeoutSeconds", 30,
                "resources", Map.of("memoryMb", 128, "cpuCores", 1),
                "gpu", Map.of("required", false)
        );
    }

    private static Map<String, Object> validConfig(String key, Object value) {
        Map<String, Object> config = new HashMap<>(validConfig());
        config.put(key, value);
        return config;
    }

    private static Map<String, Object> validWorkspaceConfig() {
        return Map.of(
                "artifactId", WORKSPACE_ARTIFACT_ID.toString(),
                "mountPath", "/workspace",
                "readOnly", true
        );
    }
}
