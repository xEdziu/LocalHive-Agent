package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.app.AgentPaths;
import dev.adrian.goral.localhiveagent.master.MasterTaskClient;
import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WorkspaceArtifactServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID WORKER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID EXECUTION_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    private static final UUID ARTIFACT_ID = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");
    private static final String API_KEY = "worker-api-key";
    private static final String LEASE_TOKEN = "lease-token";

    @TempDir
    private Path tempDir;

    @Test
    void shouldDownloadPackageIntoAgentWorkspaceAndUnpackIt() throws IOException {
        withTempHome(() -> {
            FakeMasterTaskClient taskClient = new FakeMasterTaskClient();
            WorkspaceArtifactService service = new WorkspaceArtifactService(taskClient, new WorkspacePackageUnpacker());

            PreparedWorkspace preparedWorkspace = service.prepare(
                    new AgentExecutionContext(CLOCK, "http://localhost:8080", WORKER_ID, API_KEY),
                    payload(),
                    new DockerWorkspaceConfig(ARTIFACT_ID, "/workspace", true)
            );

            assertEquals(AgentPaths.executionWorkspaceUnpackDirectory(EXECUTION_ID), preparedWorkspace.directory());
            assertTrue(Files.exists(AgentPaths.executionWorkspacePackagePath(EXECUTION_ID)));
            assertEquals("hello", Files.readString(preparedWorkspace.directory().resolve("main.txt")));
            assertEquals("http://localhost:8080", taskClient.masterBaseUrl);
            assertEquals(WORKER_ID, taskClient.workerId);
            assertEquals(EXECUTION_ID, taskClient.executionId);
            assertEquals(ARTIFACT_ID, taskClient.artifactId);
            assertEquals(API_KEY, taskClient.apiKey);
            assertEquals(LEASE_TOKEN, taskClient.leaseToken);
        });
    }

    @Test
    void shouldRejectSymlinkedExecutionDirectoryBeforeDownload() throws IOException {
        withTempHome(() -> {
            Path outside = tempDir.resolve("outside-execution");
            Files.createDirectories(outside);
            Path workspacesRoot = tempDir.resolve(".localhive-agent").resolve("workspaces");
            Files.createDirectories(workspacesRoot);
            Path executionLink = workspacesRoot.resolve(EXECUTION_ID.toString());
            assumeTrue(createDirectorySymlink(executionLink, outside), "Directory symlinks are not available.");
            FakeMasterTaskClient taskClient = new FakeMasterTaskClient();
            WorkspaceArtifactService service = new WorkspaceArtifactService(taskClient, new WorkspacePackageUnpacker());

            assertThrows(
                    WorkspaceUnpackException.class,
                    () -> service.prepare(
                            new AgentExecutionContext(CLOCK, "http://localhost:8080", WORKER_ID, API_KEY),
                            payload(),
                            new DockerWorkspaceConfig(ARTIFACT_ID, "/workspace", true)
                    )
            );
            assertEquals(0, taskClient.calls);
        });
    }

    @Test
    void shouldRejectSymlinkedWorkspacesParentBeforeDownload() throws IOException {
        withTempHome(() -> {
            Path agentRoot = tempDir.resolve(".localhive-agent");
            Files.createDirectories(agentRoot);
            Path outside = tempDir.resolve("outside-workspaces");
            Files.createDirectories(outside);
            Path workspacesLink = agentRoot.resolve("workspaces");
            assumeTrue(createDirectorySymlink(workspacesLink, outside), "Directory symlinks are not available.");
            FakeMasterTaskClient taskClient = new FakeMasterTaskClient();
            WorkspaceArtifactService service = new WorkspaceArtifactService(taskClient, new WorkspacePackageUnpacker());

            assertThrows(
                    WorkspaceUnpackException.class,
                    () -> service.prepare(
                            new AgentExecutionContext(CLOCK, "http://localhost:8080", WORKER_ID, API_KEY),
                            payload(),
                            new DockerWorkspaceConfig(ARTIFACT_ID, "/workspace", true)
                    )
            );
            assertEquals(0, taskClient.calls);
        });
    }

    private void withTempHome(ThrowingRunnable action) throws IOException {
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tempDir.toString());
            action.run();
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    private static boolean createDirectorySymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static ClaimedExecutionPayload payload() {
        return new ClaimedExecutionPayload(
                EXECUTION_ID,
                AgentExecutorRegistry.DOCKER_WORKLOAD_EXECUTOR_ID,
                AgentExecutorRegistry.DOCKER_WORKLOAD_CONTRACT_VERSION,
                Map.of(),
                128,
                1,
                false,
                LEASE_TOKEN,
                "2026-07-17T12:10:00"
        );
    }

    private static final class FakeMasterTaskClient extends MasterTaskClient {

        private String masterBaseUrl;
        private UUID workerId;
        private UUID executionId;
        private UUID artifactId;
        private String apiKey;
        private String leaseToken;
        private int calls;

        @Override
        public void downloadExecutionArtifact(String masterBaseUrl,
                                              UUID workerId,
                                              UUID executionId,
                                              UUID artifactId,
                                              String apiKey,
                                              String leaseToken,
                                              Path targetFile) {
            calls++;
            this.masterBaseUrl = masterBaseUrl;
            this.workerId = workerId;
            this.executionId = executionId;
            this.artifactId = artifactId;
            this.apiKey = apiKey;
            this.leaseToken = leaseToken;
            try {
                writeZip(targetFile);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static void writeZip(Path targetFile) throws IOException {
            Files.createDirectories(targetFile.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(targetFile))) {
                zip.putNextEntry(new ZipEntry("main.txt"));
                zip.write("hello".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}
