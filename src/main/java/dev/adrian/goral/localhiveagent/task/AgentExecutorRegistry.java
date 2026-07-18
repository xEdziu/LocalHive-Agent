package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.ConfigService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AgentExecutorRegistry {

    public static final String NO_OP_EXECUTOR_ID = "localhive.no-op";
    public static final int NO_OP_CONTRACT_VERSION = 1;
    public static final String DOCKER_WORKLOAD_EXECUTOR_ID = "localhive.docker.workload";
    public static final int DOCKER_WORKLOAD_CONTRACT_VERSION = 1;

    private final Map<ExecutorKey, AgentExecutor> executors = new ConcurrentHashMap<>();

    public static AgentExecutorRegistry withDefaultExecutors() {
        return withDefaultExecutors(DockerWorkloadExecutor::new);
    }

    public static AgentExecutorRegistry withDefaultExecutors(ConfigService configService) {
        Objects.requireNonNull(configService, "configService is required");
        return withDefaultExecutors(() -> new DockerWorkloadExecutor(
                () -> configService.load().docker(),
                new DockerWorkloadConfigParser(),
                new DockerCommandBuilder(),
                new DockerCliAvailabilityChecker(),
                new ProcessDockerCommandRunner()
        ));
    }

    private static AgentExecutorRegistry withDefaultExecutors(Supplier<AgentExecutor> dockerExecutorFactory) {
        AgentExecutorRegistry registry = new AgentExecutorRegistry();
        registry.register(NO_OP_EXECUTOR_ID, NO_OP_CONTRACT_VERSION, new NoOpAgentExecutor());
        registry.register(
                DOCKER_WORKLOAD_EXECUTOR_ID,
                DOCKER_WORKLOAD_CONTRACT_VERSION,
                dockerExecutorFactory.get()
        );
        return registry;
    }

    public void register(String executorId, int executorContractVersion, AgentExecutor executor) {
        executors.put(
                new ExecutorKey(executorId, executorContractVersion),
                Objects.requireNonNull(executor, "executor is required")
        );
    }

    public Optional<AgentExecutor> findExecutor(String executorId, int executorContractVersion) {
        return Optional.ofNullable(executors.get(new ExecutorKey(executorId, executorContractVersion)));
    }

    private record ExecutorKey(String executorId, int executorContractVersion) {

        private ExecutorKey {
            if (executorId == null || executorId.isBlank()) {
                throw new IllegalArgumentException("executorId cannot be blank.");
            }
        }
    }
}
