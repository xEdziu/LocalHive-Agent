package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.task.CurrentExecution;

import java.util.Optional;

record CurrentExecutionViewModel(
        boolean active,
        String title,
        String status,
        String executorLabel,
        String executorTechnicalInfo,
        String executionId,
        String duration,
        String workspace,
        String lastError
) {

    static CurrentExecutionViewModel from(Optional<CurrentExecution> execution) {
        if (execution.isEmpty()) {
            return empty();
        }

        CurrentExecution currentExecution = execution.get();
        return new CurrentExecutionViewModel(
                true,
                ExecutionDisplayFormatter.executionTitle(
                        currentExecution.displayName(),
                        currentExecution.executorId()
                ),
                currentExecution.status().name(),
                ExecutionDisplayFormatter.executorLabel(currentExecution.executorId()),
                ExecutionDisplayFormatter.executorTechnicalInfo(
                        currentExecution.executorId(),
                        currentExecution.executorContractVersion()
                ),
                ExecutionDisplayFormatter.uuid(currentExecution.executionId()),
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.normalize(currentExecution.lastError())
        );
    }

    static CurrentExecutionViewModel empty() {
        return new CurrentExecutionViewModel(
                false,
                "No execution running",
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE,
                ExecutionDisplayFormatter.NO_VALUE
        );
    }
}
