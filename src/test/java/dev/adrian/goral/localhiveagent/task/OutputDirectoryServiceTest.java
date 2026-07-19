package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.app.AgentPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OutputDirectoryServiceTest {

    private static final UUID EXECUTION_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

    @TempDir
    private Path tempDir;

    @Test
    void shouldCreateExecutionOutputDirectoryUnderAgentOutputs() throws IOException {
        withTempHome(() -> {
            PreparedOutputDirectory outputDirectory = new OutputDirectoryService().prepare(EXECUTION_ID);

            assertEquals(AgentPaths.executionOutputDirectory(EXECUTION_ID), outputDirectory.directory());
            assertTrue(Files.isDirectory(outputDirectory.directory()));
            assertTrue(outputDirectory.directory().endsWith(Path.of(
                    ".localhive-agent",
                    "outputs",
                    EXECUTION_ID.toString(),
                    "output"
            )));
        });
    }

    @Test
    void shouldRejectSymlinkedExecutionDirectory() throws IOException {
        withTempHome(() -> {
            Path outside = tempDir.resolve("outside-execution-output");
            Files.createDirectories(outside);
            Path outputsRoot = tempDir.resolve(".localhive-agent").resolve("outputs");
            Files.createDirectories(outputsRoot);
            Path executionLink = outputsRoot.resolve(EXECUTION_ID.toString());
            assumeTrue(createDirectorySymlink(executionLink, outside), "Directory symlinks are not available.");

            assertThrows(OutputDirectoryInvalidException.class, () -> new OutputDirectoryService().prepare(EXECUTION_ID));
        });
    }

    @Test
    void shouldRejectSymlinkedOutputDirectory() throws IOException {
        withTempHome(() -> {
            Path outside = tempDir.resolve("outside-output");
            Files.createDirectories(outside);
            Path executionRoot = tempDir.resolve(".localhive-agent")
                    .resolve("outputs")
                    .resolve(EXECUTION_ID.toString());
            Files.createDirectories(executionRoot);
            Path outputLink = executionRoot.resolve("output");
            assumeTrue(createDirectorySymlink(outputLink, outside), "Directory symlinks are not available.");

            assertThrows(OutputDirectoryInvalidException.class, () -> new OutputDirectoryService().prepare(EXECUTION_ID));
        });
    }

    @Test
    void shouldRejectFileWhereOutputParentDirectoryIsExpected() throws IOException {
        withTempHome(() -> {
            Path agentRoot = tempDir.resolve(".localhive-agent");
            Files.createDirectories(agentRoot);
            Files.writeString(agentRoot.resolve("outputs"), "not a directory");

            assertThrows(OutputDirectoryInvalidException.class, () -> new OutputDirectoryService().prepare(EXECUTION_ID));
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

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}
