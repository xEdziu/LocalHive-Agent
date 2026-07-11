package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatScheduler;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationService;
import dev.adrian.goral.localhiveagent.master.RegistrationClient;
import dev.adrian.goral.localhiveagent.system.OshiSystemInfoProvider;
import dev.adrian.goral.localhiveagent.system.SystemInfoProvider;
import dev.adrian.goral.localhiveagent.security.CredentialStore;
import dev.adrian.goral.localhiveagent.security.CredentialStoreFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentRuntime implements AutoCloseable {

    private final ConfigService configService;
    private final SystemInfoProvider systemInfoProvider;
    private final RegistrationClient registrationClient;
    private final AgentRegistrationService agentRegistrationService;
    private final HeartbeatScheduler heartbeatScheduler;
    private final ExecutorService backgroundExecutor;
    private final CredentialStore credentialStore;
    private final AtomicBoolean closed;

    private AgentRuntime(
            ConfigService configService,
            SystemInfoProvider systemInfoProvider,
            RegistrationClient registrationClient,
            AgentRegistrationService agentRegistrationService,
            HeartbeatScheduler heartbeatScheduler,
            ExecutorService backgroundExecutor,
            CredentialStore credentialStore
    ) {
        this.configService = configService;
        this.systemInfoProvider = systemInfoProvider;
        this.registrationClient = registrationClient;
        this.agentRegistrationService = agentRegistrationService;
        this.heartbeatScheduler = heartbeatScheduler;
        this.backgroundExecutor = backgroundExecutor;
        this.credentialStore = credentialStore;
        this.closed = new AtomicBoolean(false);
    }

    public static AgentRuntime createDefault() {
        Path configDirectory = Path.of(System.getProperty("user.home"), ".localhive-agent");
        Path configPath = configDirectory.resolve("config.json");

        ConfigService configService = new ConfigService(configPath);
        SystemInfoProvider systemInfoProvider = new OshiSystemInfoProvider();
        RegistrationClient registrationClient = new RegistrationClient();
        CredentialStore credentialStore = CredentialStoreFactory.createDefault(configDirectory);

        AgentRegistrationService agentRegistrationService = new AgentRegistrationService(
                configService,
                systemInfoProvider,
                registrationClient
        );

        HeartbeatScheduler heartbeatScheduler = new HeartbeatScheduler(
                configService,
                credentialStore,
                registrationClient
        );

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
                backgroundExecutor,
                credentialStore
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

    public CredentialStore credentialStore() {
        return credentialStore;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        heartbeatScheduler.close();
        backgroundExecutor.shutdownNow();
    }
}
