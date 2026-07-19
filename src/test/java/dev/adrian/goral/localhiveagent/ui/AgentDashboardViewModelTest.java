package dev.adrian.goral.localhiveagent.ui;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.task.AgentExecutorRegistry;
import dev.adrian.goral.localhiveagent.task.CurrentExecution;
import dev.adrian.goral.localhiveagent.task.CurrentExecutionStatus;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryEntry;
import dev.adrian.goral.localhiveagent.task.history.AgentTaskHistoryStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDashboardViewModelTest {

    private static final UUID EXECUTION_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    private static final Instant BASE_TIME = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void shouldFormatExecutorFriendlyLabels() {
        assertEquals("NO-OP", ExecutionDisplayFormatter.executorLabel(AgentExecutorRegistry.NO_OP_EXECUTOR_ID));
        assertEquals(
                "Docker workload",
                ExecutionDisplayFormatter.executorLabel(AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID)
        );
        assertEquals("adrian.custom-task", ExecutionDisplayFormatter.executorLabel(" adrian.custom-task "));
        assertEquals("-", ExecutionDisplayFormatter.executorLabel("   "));
    }

    @Test
    void shouldPreferDisplayNameForPrimaryExecutionTitle() {
        assertEquals("Output Artifact Smoke Test", ExecutionDisplayFormatter.executionTitle(
                " Output Artifact Smoke Test ",
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID
        ));
        assertEquals("NO-OP smoke test", ExecutionDisplayFormatter.executionTitle(
                null,
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID
        ));
        assertEquals("Docker workload", ExecutionDisplayFormatter.executionTitle(
                "   ",
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID
        ));
    }

    @Test
    void shouldFormatDurationsAndEmptyValues() {
        assertEquals("655 ms", ExecutionDisplayFormatter.duration(655L));
        assertEquals("1.2 s", ExecutionDisplayFormatter.duration(1200L));
        assertEquals("-", ExecutionDisplayFormatter.duration(null));
        assertEquals("-", ExecutionDisplayFormatter.normalize(null));
        assertEquals("-", ExecutionDisplayFormatter.normalize("   "));
    }

    @Test
    void shouldBuildCurrentExecutionViewModelWithoutLeaseToken() {
        CurrentExecutionViewModel viewModel = CurrentExecutionViewModel.from(Optional.of(new CurrentExecution(
                EXECUTION_ID,
                "Output Artifact Smoke Test",
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION,
                "raw-lease-token",
                LocalDateTime.parse("2026-07-17T12:05:00"),
                CurrentExecutionStatus.RUNNING,
                ""
        )));

        assertEquals(true, viewModel.active());
        assertEquals("Output Artifact Smoke Test", viewModel.title());
        assertEquals("RUNNING", viewModel.status());
        assertEquals("Docker workload", viewModel.executorLabel());
        assertEquals("localhive.docker.workload / contract v1", viewModel.executorTechnicalInfo());
        assertEquals(EXECUTION_ID.toString(), viewModel.executionId());
        assertEquals("-", viewModel.duration());
        assertEquals("-", viewModel.workspace());
        assertEquals("-", viewModel.lastError());
    }

    @Test
    void shouldBuildEmptyCurrentExecutionViewModel() {
        CurrentExecutionViewModel viewModel = CurrentExecutionViewModel.from(Optional.empty());

        assertEquals(false, viewModel.active());
        assertEquals("No execution running", viewModel.title());
        assertEquals("-", viewModel.status());
    }

    @Test
    void shouldBuildTaskHistorySummaryWithDisplayNameAndDuration() {
        TaskHistorySummaryViewModel viewModel = TaskHistorySummaryViewModel.from(Optional.of(historyEntry(
                "Output Artifact Smoke Test",
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentTaskHistoryStatus.SUCCEEDED,
                655L,
                "",
                ""
        )), 3);

        assertEquals(true, viewModel.present());
        assertEquals("3 records", viewModel.totalCountLabel());
        assertEquals("Output Artifact Smoke Test", viewModel.title());
        assertEquals("SUCCEEDED", viewModel.status());
        assertEquals("Docker workload", viewModel.executorLabel());
        assertEquals("655 ms", viewModel.duration());
        assertEquals("-", viewModel.issue());
    }

    @Test
    void shouldUseFriendlyHistoryFallbackWhenDisplayNameMissing() {
        TaskHistorySummaryViewModel viewModel = TaskHistorySummaryViewModel.from(Optional.of(historyEntry(
                null,
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentTaskHistoryStatus.FAILED,
                1200L,
                "DOCKER_WORKLOAD_FAILED",
                "exit code 7"
        )), 1);

        assertEquals("Docker workload", viewModel.title());
        assertEquals("1 record", viewModel.totalCountLabel());
        assertEquals("1.2 s", viewModel.duration());
        assertEquals("DOCKER_WORKLOAD_FAILED: exit code 7", viewModel.issue());
    }

    @Test
    void shouldBuildEmptyTaskHistorySummary() {
        TaskHistorySummaryViewModel viewModel = TaskHistorySummaryViewModel.from(Optional.empty(), 0);

        assertEquals(false, viewModel.present());
        assertEquals("No task history yet", viewModel.title());
        assertEquals("0 records", viewModel.totalCountLabel());
        assertEquals("-", viewModel.status());
    }

    @Test
    void shouldBuildDockerPolicyViewModel() {
        DockerPolicyViewModel viewModel = DockerPolicyViewModel.from(new DockerPolicy(
                true,
                List.of("alpine:3.20", "localhive/runner:1"),
                2048,
                4,
                false
        ));

        assertEquals("Yes", viewModel.enabled());
        assertEquals("alpine:3.20, localhive/runner:1", viewModel.allowedImages());
        assertEquals("2048 MB", viewModel.maxMemoryMb());
        assertEquals("4", viewModel.maxCpuCores());
        assertEquals("No", viewModel.gpuAllowed());
    }

    @Test
    void shouldUseDefaultDockerPolicyWhenMissing() {
        DockerPolicyViewModel viewModel = DockerPolicyViewModel.from(null);

        assertEquals("Yes", viewModel.enabled());
        assertEquals("alpine:3.20", viewModel.allowedImages());
        assertEquals("4096 MB", viewModel.maxMemoryMb());
        assertEquals("8", viewModel.maxCpuCores());
        assertEquals("No", viewModel.gpuAllowed());
    }

    private static AgentTaskHistoryEntry historyEntry(String displayName,
                                                      String executorId,
                                                      AgentTaskHistoryStatus status,
                                                      Long durationMs,
                                                      String failureCode,
                                                      String failureMessage) {
        return new AgentTaskHistoryEntry(
                1,
                EXECUTION_ID,
                displayName,
                executorId,
                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION,
                status,
                BASE_TIME,
                BASE_TIME.plusMillis(100),
                BASE_TIME.plusMillis(100 + (durationMs == null ? 0 : durationMs)),
                durationMs,
                failureCode,
                failureMessage,
                "",
                BASE_TIME,
                BASE_TIME.plusMillis(100)
        );
    }
}
