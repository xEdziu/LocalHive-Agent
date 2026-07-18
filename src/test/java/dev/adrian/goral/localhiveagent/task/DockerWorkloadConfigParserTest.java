package dev.adrian.goral.localhiveagent.task;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerWorkloadConfigParserTest {

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
    }

    @Test
    void shouldRejectNonAllowlistedImage() {
        Map<String, Object> config = validConfig("image", "ubuntu:24.04");

        assertThrows(DockerImageNotAllowedException.class, () -> parser.parse(config));
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
    void shouldRejectInvalidTimeout() {
        Map<String, Object> config = validConfig("timeoutSeconds", 301);

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectInvalidMemory() {
        Map<String, Object> config = validConfig("resources", Map.of("memoryMb", 15, "cpuCores", 1));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
    }

    @Test
    void shouldRejectInvalidCpu() {
        Map<String, Object> config = validConfig("resources", Map.of("memoryMb", 128, "cpuCores", 9));

        assertThrows(DockerWorkloadConfigurationException.class, () -> parser.parse(config));
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
}
