package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class DockerWorkloadExecutor implements AgentExecutor {

    public static final String INVALID_CONFIGURATION_FAILURE_CODE = "DOCKER_WORKLOAD_INVALID_CONFIGURATION";
    public static final String IMAGE_NOT_ALLOWED_FAILURE_CODE = "DOCKER_IMAGE_NOT_ALLOWED";
    public static final String DOCKER_UNAVAILABLE_FAILURE_CODE = "DOCKER_UNAVAILABLE";
    public static final String TIMEOUT_FAILURE_CODE = "DOCKER_WORKLOAD_TIMEOUT";
    public static final String WORKLOAD_FAILED_FAILURE_CODE = "DOCKER_WORKLOAD_FAILED";

    private static final Logger log = LoggerFactory.getLogger(DockerWorkloadExecutor.class);
    private static final int FAILURE_MESSAGE_OUTPUT_LIMIT = 512;

    private final DockerWorkloadConfigParser configParser;
    private final DockerCommandBuilder commandBuilder;
    private final DockerAvailabilityChecker availabilityChecker;
    private final DockerCommandRunner commandRunner;

    public DockerWorkloadExecutor() {
        this(
                new DockerWorkloadConfigParser(),
                new DockerCommandBuilder(),
                new DockerCliAvailabilityChecker(),
                new ProcessDockerCommandRunner()
        );
    }

    DockerWorkloadExecutor(DockerWorkloadConfigParser configParser,
                           DockerCommandBuilder commandBuilder,
                           DockerAvailabilityChecker availabilityChecker,
                           DockerCommandRunner commandRunner) {
        this.configParser = Objects.requireNonNull(configParser, "configParser is required");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder is required");
        this.availabilityChecker = Objects.requireNonNull(availabilityChecker, "availabilityChecker is required");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner is required");
    }

    @Override
    public AgentExecutionResult execute(ClaimedExecutionPayload payload, AgentExecutionContext context) {
        Objects.requireNonNull(payload, "payload is required");

        DockerWorkloadConfig config;
        try {
            config = configParser.parse(payload.configuration());
        } catch (DockerImageNotAllowedException exception) {
            return AgentExecutionResult.failed(IMAGE_NOT_ALLOWED_FAILURE_CODE, exception.getMessage());
        } catch (DockerWorkloadConfigurationException exception) {
            return AgentExecutionResult.failed(INVALID_CONFIGURATION_FAILURE_CODE, exception.getMessage());
        }

        if (!isDockerAvailable()) {
            return AgentExecutionResult.failed(DOCKER_UNAVAILABLE_FAILURE_CODE, "Docker CLI is unavailable.");
        }

        List<String> command = commandBuilder.build(config);
        DockerCommandResult result;
        try {
            result = commandRunner.run(command, Duration.ofSeconds(config.timeoutSeconds()));
        } catch (RuntimeException exception) {
            return AgentExecutionResult.failed(
                    WORKLOAD_FAILED_FAILURE_CODE,
                    shortMessage("Docker command failed.", exception.getMessage())
            );
        }
        log.info(
                "Docker workload finished. executionId={} image={} exitCode={} durationMs={} stdoutLength={} stderrLength={}",
                payload.executionId(),
                config.image(),
                result.exitCode(),
                result.durationMs(),
                result.stdout().length(),
                result.stderr().length()
        );

        if (result.failedToStart()) {
            return AgentExecutionResult.failed(
                    WORKLOAD_FAILED_FAILURE_CODE,
                    shortMessage("Docker command failed to start.", result.startFailureMessage())
            );
        }
        if (result.timedOut()) {
            return AgentExecutionResult.failed(
                    TIMEOUT_FAILURE_CODE,
                    "Docker workload exceeded timeout of " + config.timeoutSeconds() + " seconds."
            );
        }
        if (result.exitCode() != 0) {
            return AgentExecutionResult.failed(
                    WORKLOAD_FAILED_FAILURE_CODE,
                    shortMessage("Docker workload exited with code " + result.exitCode() + ".", result.stderr())
            );
        }

        return AgentExecutionResult.succeeded();
    }

    private boolean isDockerAvailable() {
        try {
            return availabilityChecker.isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String shortMessage(String prefix, String detail) {
        String normalizedDetail = detail == null ? "" : detail.trim();
        String message = normalizedDetail.isBlank() ? prefix : prefix + " " + normalizedDetail;
        return message.length() <= FAILURE_MESSAGE_OUTPUT_LIMIT
                ? message
                : message.substring(0, FAILURE_MESSAGE_OUTPUT_LIMIT);
    }
}
