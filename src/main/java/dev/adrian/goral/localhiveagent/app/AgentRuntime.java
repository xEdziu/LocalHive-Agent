package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatScheduler;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationService;
import dev.adrian.goral.localhiveagent.master.RegistrationClient;
import dev.adrian.goral.localhiveagent.system.OshiSystemInfoProvider;
import dev.adrian.goral.localhiveagent.system.SystemInfoProvider;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AgentRuntime implements AutoCloseable {

    private final ConfigService configService;
    private final SystemInfoProvider systemInfoProvider;
    private final RegistrationClient registrationClient;
    private final AgentRegistrationService agentRegistrationService;
    private final HeartbeatScheduler heartbeatScheduler;
    private final ExecutorService backgroundExecutor;

    private AgentRuntime(
            ConfigService configService,
            SystemInfoProvider systemInfoProvider,
            RegistrationClient registrationClient,
            AgentRegistrationService agentRegistrationService,
            HeartbeatScheduler heartbeatScheduler,
            ExecutorService backgroundExecutor
    ) {
        this.configService = configService;
        this.systemInfoProvider = systemInfoProvider;
        this.registrationClient = registrationClient;
        this.agentRegistrationService = agentRegistrationService;
        this.heartbeatScheduler = heartbeatScheduler;
        this.backgroundExecutor = backgroundExecutor;
    }

    public static AgentRuntime createDefault() {
        Path configPath = Path.of(System.getProperty("user.home"), ".localhive-agent", "config.json");

        ConfigService configService = new ConfigService(configPath);
        SystemInfoProvider systemInfoProvider = new OshiSystemInfoProvider();
        RegistrationClient registrationClient = new RegistrationClient();

        AgentRegistrationService agentRegistrationService = new AgentRegistrationService(
                configService,
                systemInfoProvider,
                registrationClient
        );

        HeartbeatScheduler heartbeatScheduler = new HeartbeatScheduler(configService, registrationClient);

        ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "localhive-agent-background");
            thread.setDaemon(true);
            return thread;
        });

        return new AgentRuntime(
                configService,
                systemInfoProvider,
                registrationClient,
                agentRegistrationService,
                heartbeatScheduler,
                backgroundExecutor
        );
    }

    public ConfigService configService() {
        return configService;
    }

    public SystemInfoProvider systemInfoProvider() {
        return systemInfoProvider;
    }

    public RegistrationClient registrationClient() {
        return registrationClient;
    }

    public AgentRegistrationService agentRegistrationService() {
        return agentRegistrationService;
    }

    public HeartbeatScheduler heartbeatScheduler() {
        return heartbeatScheduler;
    }

    public ExecutorService backgroundExecutor() {
        return backgroundExecutor;
    }

    @Override
    public void close() {
        heartbeatScheduler.close();
        backgroundExecutor.shutdownNow();
    }
}