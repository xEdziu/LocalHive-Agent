package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;
import dev.adrian.goral.localhiveagent.master.MasterClientException;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class DockerWorkloadExecutor implements AgentExecutor {

    public static final String DOCKER_DISABLED_FAILURE_CODE = "DOCKER_DISABLED";
    public static final String INVALID_CONFIGURATION_FAILURE_CODE = "DOCKER_WORKLOAD_INVALID_CONFIGURATION";
    public static final String IMAGE_NOT_ALLOWED_FAILURE_CODE = "DOCKER_IMAGE_NOT_ALLOWED";
    public static final String DOCKER_UNAVAILABLE_FAILURE_CODE = "DOCKER_UNAVAILABLE";
    public static final String TIMEOUT_FAILURE_CODE = "DOCKER_WORKLOAD_TIMEOUT";
    public static final String WORKLOAD_FAILED_FAILURE_CODE = "DOCKER_WORKLOAD_FAILED";
    public static final String WORKSPACE_ARTIFACT_DOWNLOAD_FAILED_CODE = "WORKSPACE_ARTIFACT_DOWNLOAD_FAILED";
    public static final String WORKSPACE_PACKAGE_INVALID_CODE = "WORKSPACE_PACKAGE_INVALID";
    public static final String WORKSPACE_UNPACK_FAILED_CODE = "WORKSPACE_UNPACK_FAILED";
    public static final String OUTPUT_DIRECTORY_PREPARATION_FAILED_CODE = "OUTPUT_DIRECTORY_PREPARATION_FAILED";
    public static final String OUTPUT_DIRECTORY_INVALID_CODE = "OUTPUT_DIRECTORY_INVALID";
    public static final String OUTPUT_ARTIFACT_UPLOAD_FAILED_CODE = "OUTPUT_ARTIFACT_UPLOAD_FAILED";

    private static final Logger log = LoggerFactory.getLogger(DockerWorkloadExecutor.class);
    private static final int FAILURE_MESSAGE_OUTPUT_LIMIT = 512;

    private final Supplier<DockerPolicy> policyProvider;
    private final DockerWorkloadConfigParser configParser;
    private final DockerCommandBuilder commandBuilder;
    private final DockerAvailabilityChecker availabilityChecker;
    private final DockerCommandRunner commandRunner;
    private final WorkspacePreparer workspacePreparer;
    private final OutputDirectoryPreparer outputDirectoryPreparer;
    private final OutputArtifactScanner outputArtifactScanner;
    private final OutputArtifactUploader outputArtifactUploader;

    public DockerWorkloadExecutor() {
        this(
                DockerPolicy::defaultPolicy,
                new DockerWorkloadConfigParser(),
                new DockerCommandBuilder(),
                new DockerCliAvailabilityChecker(),
                new ProcessDockerCommandRunner(),
                new WorkspaceArtifactService(),
                new OutputDirectoryService(),
                new OutputDirectoryScanner(),
                new MasterOutputArtifactUploader()
        );
    }

    DockerWorkloadExecutor(DockerWorkloadConfigParser configParser,
                           DockerCommandBuilder commandBuilder,
                           DockerAvailabilityChecker availabilityChecker,
                           DockerCommandRunner commandRunner) {
        this(DockerPolicy::defaultPolicy, configParser, commandBuilder, availabilityChecker, commandRunner);
    }

    DockerWorkloadExecutor(Supplier<DockerPolicy> policyProvider,
                           DockerWorkloadConfigParser configParser,
                           DockerCommandBuilder commandBuilder,
                           DockerAvailabilityChecker availabilityChecker,
                           DockerCommandRunner commandRunner) {
        this(policyProvider, configParser, commandBuilder, availabilityChecker, commandRunner, new WorkspaceArtifactService());
    }

    DockerWorkloadExecutor(Supplier<DockerPolicy> policyProvider,
                           DockerWorkloadConfigParser configParser,
                           DockerCommandBuilder commandBuilder,
                           DockerAvailabilityChecker availabilityChecker,
                           DockerCommandRunner commandRunner,
                           WorkspacePreparer workspacePreparer) {
        this(
                policyProvider,
                configParser,
                commandBuilder,
                availabilityChecker,
                commandRunner,
                workspacePreparer,
                new OutputDirectoryService(),
                new OutputDirectoryScanner(),
                new MasterOutputArtifactUploader()
        );
    }

    DockerWorkloadExecutor(Supplier<DockerPolicy> policyProvider,
                           DockerWorkloadConfigParser configParser,
                           DockerCommandBuilder commandBuilder,
                           DockerAvailabilityChecker availabilityChecker,
                           DockerCommandRunner commandRunner,
                           WorkspacePreparer workspacePreparer,
                           OutputDirectoryPreparer outputDirectoryPreparer,
                           OutputArtifactScanner outputArtifactScanner,
                           OutputArtifactUploader outputArtifactUploader) {
        this.policyProvider = Objects.requireNonNull(policyProvider, "policyProvider is required");
        this.configParser = Objects.requireNonNull(configParser, "configParser is required");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder is required");
        this.availabilityChecker = Objects.requireNonNull(availabilityChecker, "availabilityChecker is required");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner is required");
        this.workspacePreparer = Objects.requireNonNull(workspacePreparer, "workspacePreparer is required");
        this.outputDirectoryPreparer = Objects.requireNonNull(outputDirectoryPreparer, "outputDirectoryPreparer is required");
        this.outputArtifactScanner = Objects.requireNonNull(outputArtifactScanner, "outputArtifactScanner is required");
        this.outputArtifactUploader = Objects.requireNonNull(outputArtifactUploader, "outputArtifactUploader is required");
    }

    @Override
    public AgentExecutionResult execute(ClaimedExecutionPayload payload, AgentExecutionContext context) {
        Objects.requireNonNull(payload, "payload is required");

        DockerWorkloadConfig config;
        DockerPolicy policy;
        try {
            policy = currentPolicy();
            if (!policy.enabled()) {
                return AgentExecutionResult.failed(DOCKER_DISABLED_FAILURE_CODE, "Docker workloads are disabled.");
            }
            config = configParser.parse(payload.configuration(), policy);
        } catch (DockerImageNotAllowedException exception) {
            return AgentExecutionResult.failed(IMAGE_NOT_ALLOWED_FAILURE_CODE, exception.getMessage());
        } catch (DockerWorkloadConfigurationException exception) {
            return AgentExecutionResult.failed(INVALID_CONFIGURATION_FAILURE_CODE, exception.getMessage());
        }

        if (!isDockerAvailable()) {
            return AgentExecutionResult.failed(DOCKER_UNAVAILABLE_FAILURE_CODE, "Docker CLI is unavailable.");
        }

        PreparedWorkspace preparedWorkspace;
        try {
            preparedWorkspace = prepareWorkspaceIfNeeded(config, payload, context);
        } catch (MasterClientException exception) {
            return AgentExecutionResult.failed(
                    WORKSPACE_ARTIFACT_DOWNLOAD_FAILED_CODE,
                    shortMessage("Workspace artifact download failed.", exception.getMessage())
            );
        } catch (WorkspacePackageInvalidException exception) {
            return AgentExecutionResult.failed(WORKSPACE_PACKAGE_INVALID_CODE, exception.getMessage());
        } catch (WorkspaceUnpackException exception) {
            return AgentExecutionResult.failed(WORKSPACE_UNPACK_FAILED_CODE, exception.getMessage());
        }

        PreparedOutputDirectory preparedOutputDirectory;
        try {
            preparedOutputDirectory = outputDirectoryPreparer.prepare(payload.executionId());
        } catch (OutputDirectoryPreparationException exception) {
            return AgentExecutionResult.failed(OUTPUT_DIRECTORY_PREPARATION_FAILED_CODE, exception.getMessage());
        } catch (OutputDirectoryInvalidException exception) {
            return AgentExecutionResult.failed(OUTPUT_DIRECTORY_INVALID_CODE, exception.getMessage());
        }

        List<String> command = commandBuilder.build(
                config,
                preparedWorkspace == null ? null : preparedWorkspace.directory(),
                preparedOutputDirectory.directory()
        );
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

        AgentExecutionResult dockerResult;
        if (result.exitCode() != 0) {
            dockerResult = AgentExecutionResult.failed(
                    WORKLOAD_FAILED_FAILURE_CODE,
                    shortMessage("Docker workload exited with code " + result.exitCode() + ".", result.stderr())
            );
        } else {
            dockerResult = AgentExecutionResult.succeeded();
        }

        return scanAndUploadOutputs(payload, context, preparedOutputDirectory, dockerResult);
    }

    private boolean isDockerAvailable() {
        try {
            return availabilityChecker.isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private DockerPolicy currentPolicy() {
        return Objects.requireNonNullElseGet(policyProvider.get(), DockerPolicy::defaultPolicy);
    }

    private PreparedWorkspace prepareWorkspaceIfNeeded(DockerWorkloadConfig config,
                                                       ClaimedExecutionPayload payload,
                                                       AgentExecutionContext context) {
        if (config.workspace() == null) {
            return null;
        }
        return workspacePreparer.prepare(
                Objects.requireNonNull(context, "context is required"),
                payload,
                config.workspace()
        );
    }

    private AgentExecutionResult scanAndUploadOutputs(ClaimedExecutionPayload payload,
                                                      AgentExecutionContext context,
                                                      PreparedOutputDirectory preparedOutputDirectory,
                                                      AgentExecutionResult dockerResult) {
        List<OutputArtifactFile> outputFiles;
        try {
            outputFiles = outputArtifactScanner.scan(preparedOutputDirectory.directory());
        } catch (OutputDirectoryInvalidException exception) {
            if (dockerResult.success()) {
                return AgentExecutionResult.failed(
                        OUTPUT_DIRECTORY_INVALID_CODE,
                        shortMessage("Output directory is invalid.", exception.getMessage())
                );
            }

            log.warn(
                    "Output directory scan failed after Docker failure. executionId={} preservedFailureCode={} errorType={}",
                    payload.executionId(),
                    dockerResult.failureCode(),
                    exception.getClass().getSimpleName()
            );
            return dockerResult;
        }

        if (outputFiles.isEmpty()) {
            log.info("No output artifacts found for execution {}.", payload.executionId());
            return dockerResult;
        }

        try {
            outputArtifactUploader.uploadAll(context, payload, outputFiles);
            log.info(
                    "Uploaded output artifacts for execution {}. fileCount={} totalBytes={}",
                    payload.executionId(),
                    outputFiles.size(),
                    totalBytes(outputFiles)
            );
            return dockerResult;
        } catch (RuntimeException exception) {
            if (dockerResult.success()) {
                return AgentExecutionResult.failed(
                        OUTPUT_ARTIFACT_UPLOAD_FAILED_CODE,
                        shortMessage("Output artifact upload failed.", exception.getMessage())
                );
            }

            log.warn(
                    "Output artifact upload failed after Docker failure. executionId={} preservedFailureCode={} errorType={}",
                    payload.executionId(),
                    dockerResult.failureCode(),
                    exception.getClass().getSimpleName()
            );
            return dockerResult;
        }
    }

    private static String shortMessage(String prefix, String detail) {
        String normalizedDetail = detail == null ? "" : detail.trim();
        String message = normalizedDetail.isBlank() ? prefix : prefix + " " + normalizedDetail;
        return message.length() <= FAILURE_MESSAGE_OUTPUT_LIMIT
                ? message
                : message.substring(0, FAILURE_MESSAGE_OUTPUT_LIMIT);
    }

    private static long totalBytes(List<OutputArtifactFile> outputFiles) {
        return outputFiles.stream()
                .mapToLong(OutputArtifactFile::sizeBytes)
                .sum();
    }
}
