package dev.adrian.goral.localhiveagent.app;

import dev.adrian.goral.localhiveagent.config.ConfigService;
import dev.adrian.goral.localhiveagent.heartbeat.HeartbeatScheduler;
import dev.adrian.goral.localhiveagent.master.AgentRegistrationService;
import dev.adrian.goral.localhiveagent.master.MasterTaskClient;
import dev.adrian.goral.localhiveagent.master.RegistrationClient;
import dev.adrian.goral.localhiveagent.state.AgentStateStore;
import dev.adrian.goral.localhiveagent.system.OshiSystemInfoProvider;
import dev.adrian.goral.localhiveagent.system.SystemInfoProvider;
import dev.adrian.goral.localhiveagent.security.CredentialStore;
import dev.adrian.goral.localhiveagent.security.CredentialStoreFactory;
import dev.adrian.goral.localhiveagent.task.AgentExecutorRegistry;
import dev.adrian.goral.localhiveagent.task.CurrentExecutionStore;
import dev.adrian.goral.localhiveagent.task.TaskPollingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final ConfigService configService;
    private final SystemInfoProvider systemInfoProvider;
    private final RegistrationClient registrationClient;
    private final MasterTaskClient taskClient;
    private final AgentRegistrationService agentRegistrationService;
    private final HeartbeatScheduler heartbeatScheduler;
    private final TaskPollingService taskPollingService;
    private final CurrentExecutionStore currentExecutionStore;
    private final ExecutorService backgroundExecutor;
    private final CredentialStore credentialStore;
    private final AgentStateStore agentStateStore;
    private final AtomicBoolean closed;

    private AgentRuntime(
            ConfigService configService,
            SystemInfoProvider systemInfoProvider,
            RegistrationClient registrationClient,
            MasterTaskClient taskClient,
            AgentRegistrationService agentRegistrationService,
            HeartbeatScheduler heartbeatScheduler,
            TaskPollingService taskPollingService,
            CurrentExecutionStore currentExecutionStore,
            ExecutorService backgroundExecutor,
            CredentialStore credentialStore,
            AgentStateStore agentStateStore
    ) {
        this.configService = configService;
        this.systemInfoProvider = systemInfoProvider;
        this.registrationClient = registrationClient;
        this.taskClient = taskClient;
        this.agentRegistrationService = agentRegistrationService;
        this.heartbeatScheduler = heartbeatScheduler;
        this.taskPollingService = taskPollingService;
        this.currentExecutionStore = currentExecutionStore;
        this.backgroundExecutor = backgroundExecutor;
        this.credentialStore = credentialStore;
        this.agentStateStore = agentStateStore;
        this.closed = new AtomicBoolean(false);
    }

    public static AgentRuntime createDefault() {
        Path configDirectory = AgentPaths.agentDirectory();
        Path configPath = AgentPaths.configPath();

        ConfigService configService = new ConfigService(configPath);
        SystemInfoProvider systemInfoProvider = new OshiSystemInfoProvider();
        RegistrationClient registrationClient = new RegistrationClient();
        MasterTaskClient taskClient = new MasterTaskClient();
        CredentialStore credentialStore = CredentialStoreFactory.createDefault(configDirectory);
        log.info("CredentialStore selected: {} (secure = {})",
                credentialStore.backendName(),
                credentialStore.isSecure());

        if (!credentialStore.isSecure()) {
            log.warn("Insecure CredentialStore selected. API key is stored in a local fallback file.");
        }

        var initialConfig = configService.load();
        AgentStateStore agentStateStore = AgentStateStore.fromConfig(
                initialConfig,
                initialConfig.hasMasterBaseUrl() && initialConfig.hasWorkerId() && credentialStore.hasApiKey(),
                false
        );

        AgentRegistrationService agentRegistrationService = new AgentRegistrationService(
                configService,
                systemInfoProvider,
                registrationClient
        );

        HeartbeatScheduler heartbeatScheduler = new HeartbeatScheduler(
                configService,
                credentialStore,
                registrationClient,
                agentStateStore
        );

        CurrentExecutionStore currentExecutionStore = new CurrentExecutionStore();
        TaskPollingService taskPollingService = new TaskPollingService(
                configService,
                credentialStore,
                taskClient,
                AgentExecutorRegistry.withDefaultExecutors(),
                currentExecutionStore,
                agentStateStore
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
                taskClient,
                agentRegistrationService,
                heartbeatScheduler,
                taskPollingService,
                currentExecutionStore,
                backgroundExecutor,
                credentialStore,
                agentStateStore
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

    public MasterTaskClient taskClient() {
        return taskClient;
    }

    public AgentRegistrationService agentRegistrationService() {
        return agentRegistrationService;
    }

    public HeartbeatScheduler heartbeatScheduler() {
        return heartbeatScheduler;
    }

    public TaskPollingService taskPollingService() {
        return taskPollingService;
    }

    public CurrentExecutionStore currentExecutionStore() {
        return currentExecutionStore;
    }

    public ExecutorService backgroundExecutor() {
        return backgroundExecutor;
    }

    public CredentialStore credentialStore() {
        return credentialStore;
    }

    public AgentStateStore agentStateStore() {
        return agentStateStore;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        log.info("Agent runtime closing");
        taskPollingService.close();
        heartbeatScheduler.close();
        backgroundExecutor.shutdownNow();
        agentStateStore.close();
        log.info("Agent runtime closed");
    }
}
