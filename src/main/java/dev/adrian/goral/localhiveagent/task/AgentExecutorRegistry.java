package dev.adrian.goral.localhiveagent.task;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentExecutorRegistry {

    public static final String NO_OP_EXECUTOR_ID = "localhive.no-op";
    public static final int NO_OP_CONTRACT_VERSION = 1;

    private final Map<ExecutorKey, AgentExecutor> executors = new ConcurrentHashMap<>();

    public static AgentExecutorRegistry withDefaultExecutors() {
        AgentExecutorRegistry registry = new AgentExecutorRegistry();
        registry.register(NO_OP_EXECUTOR_ID, NO_OP_CONTRACT_VERSION, new NoOpAgentExecutor());
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
