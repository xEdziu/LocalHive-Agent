package dev.adrian.goral.localhiveagent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldReturnDefaultConfigWhenFileIsMissing() {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));

        assertEquals(AgentConfig.empty(), configService.load());
    }

    @Test
    void shouldSaveAndLoadConfig() {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));
        AgentConfig config = readyConfig();

        configService.save(config);

        assertEquals(config, configService.load());
    }

    @Test
    void shouldUpdateConfigAndPersistValues() {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));

        AgentConfig updatedConfig = configService.update(config -> config
                .withMasterBaseUrl(" http://localhost:8080 ")
                .withWorkerId(workerId())
                .withSharedRamMb(4096)
                .withPauseEnabled(true)
        );

        assertEquals(updatedConfig, configService.load());
        assertEquals("http://localhost:8080", configService.load().masterBaseUrl());
        assertTrue(configService.load().pauseEnabled());
    }

    @Test
    void shouldNotSerializeApiKey() throws Exception {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));

        configService.save(readyConfig());

        String json = Files.readString(tempDir.resolve("config.json"));
        assertFalse(json.contains("apiKey"));
        assertFalse(json.contains("test-api-key"));
    }

    @Test
    void shouldIgnoreUnknownLegacyJsonProperty() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "masterBaseUrl": "http://localhost:8080",
                  "workerId": "123e4567-e89b-12d3-a456-426614174000",
                  "sharedRamMb": 4096,
                  "pauseEnabled": true,
                  "legacyApiKey": "test-api-key"
                }
                """);

        AgentConfig config = new ConfigService(configPath).load();

        assertEquals("http://localhost:8080", config.masterBaseUrl());
        assertEquals(workerId(), config.workerId());
        assertEquals(4096, config.sharedRamMb());
        assertTrue(config.pauseEnabled());
        assertEquals(DockerPolicy.defaultPolicy(), config.docker());
    }

    @Test
    void shouldDefaultDockerPolicyForLegacyConfigWithoutDockerSection() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "masterBaseUrl": "http://localhost:8080",
                  "workerId": "123e4567-e89b-12d3-a456-426614174000",
                  "sharedRamMb": 4096,
                  "pauseEnabled": true
                }
                """);

        AgentConfig config = new ConfigService(configPath).load();

        assertEquals(DockerPolicy.defaultPolicy(), config.docker());
    }

    @Test
    void shouldDefaultMissingDockerPolicyFields() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "masterBaseUrl": "http://localhost:8080",
                  "workerId": "123e4567-e89b-12d3-a456-426614174000",
                  "sharedRamMb": 4096,
                  "pauseEnabled": true,
                  "docker": {}
                }
                """);

        AgentConfig config = new ConfigService(configPath).load();

        assertEquals(DockerPolicy.defaultPolicy(), config.docker());
    }

    @Test
    void shouldSaveAndLoadDockerPolicy() {
        ConfigService configService = new ConfigService(tempDir.resolve("config.json"));
        DockerPolicy policy = new DockerPolicy(false, List.of("localhive/test-runner:1"), 256, 2, false);
        AgentConfig config = readyConfig().withDocker(policy);

        configService.save(config);

        assertEquals(policy, configService.load().docker());
    }

    @Test
    void shouldWriteFormattedValidJson() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        ConfigService configService = new ConfigService(configPath);

        configService.save(readyConfig());

        String json = Files.readString(configPath);
        assertTrue(json.contains(System.lineSeparator()) || json.contains("\n"));
        assertDoesNotThrow(() -> JsonMapper.builder().build().readValue(json, AgentConfig.class));
    }

    @Test
    void shouldCreateConfigDirectoryAutomatically() {
        Path configPath = tempDir.resolve("nested").resolve("config").resolve("config.json");
        ConfigService configService = new ConfigService(configPath);

        configService.save(readyConfig());

        assertTrue(Files.exists(configPath));
    }

    @Test
    void shouldThrowConfigExceptionForCorruptedJson() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, "{ invalid json");

        assertThrows(ConfigException.class, () -> new ConfigService(configPath).load());
    }

    @Test
    void shouldLeaveValidTargetFileAfterRepeatedSaves() {
        Path configPath = tempDir.resolve("config.json");
        ConfigService configService = new ConfigService(configPath);

        configService.save(AgentConfig.empty());
        configService.save(readyConfig());

        assertEquals(readyConfig(), configService.load());
        assertDoesNotThrow(() -> JsonMapper.builder().build().readValue(
                Files.readString(configPath),
                AgentConfig.class
        ));
    }

    private static AgentConfig readyConfig() {
        return new AgentConfig(
                "http://localhost:8080",
                workerId(),
                4096,
                true
        );
    }

    private static UUID workerId() {
        return UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    }
}
