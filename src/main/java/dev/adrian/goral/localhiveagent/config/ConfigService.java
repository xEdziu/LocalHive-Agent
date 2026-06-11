package dev.adrian.goral.localhiveagent.config;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.UnaryOperator;

public class ConfigService {

    private final Path configPath;
    private final JsonMapper jsonMapper;
    private final ReentrantReadWriteLock lock;

    public ConfigService(Path configPath) {
        this.configPath = configPath;
        this.jsonMapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.lock = new ReentrantReadWriteLock();
    }

    public Path configPath() {
        return configPath;
    }

    public AgentConfig loadOrCreate() {
        lock.writeLock().lock();

        try {
            ensureConfigDirectoryExists();

            if (Files.notExists(configPath)) {
                AgentConfig emptyConfig = AgentConfig.empty();
                writeConfig(emptyConfig);
                return emptyConfig;
            }

            return readConfig();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public AgentConfig load() {
        lock.readLock().lock();

        try {
            if (Files.notExists(configPath)) {
                return AgentConfig.empty();
            }

            return readConfig();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void save(AgentConfig config) {
        lock.writeLock().lock();

        try {
            ensureConfigDirectoryExists();
            writeConfig(config);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public AgentConfig update(UnaryOperator<AgentConfig> updater) {
        lock.writeLock().lock();

        try {
            ensureConfigDirectoryExists();

            AgentConfig currentConfig = Files.exists(configPath)
                    ? readConfig()
                    : AgentConfig.empty();

            AgentConfig updatedConfig = updater.apply(currentConfig);
            writeConfig(updatedConfig);

            return updatedConfig;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public AgentConfig updateMasterBaseUrl(String masterBaseUrl) {
        return update(config -> config.withMasterBaseUrl(masterBaseUrl));
    }

    public AgentConfig updateWorkerId(UUID workerId) {
        return update(config -> config.withWorkerId(workerId));
    }

    public AgentConfig updateSharedRamMb(int sharedRamMb) {
        return update(config -> config.withSharedRamMb(sharedRamMb));
    }

    public AgentConfig updatePauseEnabled(boolean pauseEnabled) {
        return update(config -> config.withPauseEnabled(pauseEnabled));
    }

    private AgentConfig readConfig() {
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            AgentConfig config = jsonMapper.readValue(inputStream, AgentConfig.class);
            return config == null ? AgentConfig.empty() : config;
        } catch (IOException | RuntimeException exception) {
            throw new ConfigException("Failed to read agent configuration from: " + configPath, exception);
        }
    }

    private void writeConfig(AgentConfig config) {
        Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");

        try (OutputStream outputStream = Files.newOutputStream(tempPath)) {
            jsonMapper.writeValue(outputStream, config);
            outputStream.flush();

            Files.move(
                    tempPath,
                    configPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException | RuntimeException exception) {
            throw new ConfigException("Failed to write agent configuration to: " + configPath, exception);
        }
    }

    private void ensureConfigDirectoryExists() {
        Path parent = configPath.getParent();

        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new ConfigException("Failed to create config directory: " + parent, exception);
        }
    }
}