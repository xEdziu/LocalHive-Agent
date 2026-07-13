package dev.adrian.goral.localhiveagent.logging;

import dev.adrian.goral.localhiveagent.master.MasterClientErrorMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoggingTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @TempDir
    private Path tempDir;

    @AfterEach
    void tearDown() {
        AgentLogging.closeCurrent();
    }

    @Test
    void shouldCreateLogDirectoryActiveFileAndWriteUtf8Message() throws IOException {
        AgentLogPolicy policy = testPolicy(1024, 3);
        ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();

        try (PrintStream console = console(consoleBuffer)) {
            AgentLogging.initialize(policy, console, BoundedLogFileOutputStream::new);

            System.err.println("LocalHive UTF-8 log message: Zażółć gęślą jaźń");
            System.err.flush();
        }

        AgentLogging.closeCurrent();

        assertTrue(Files.isDirectory(policy.logDirectory()));
        assertTrue(Files.isRegularFile(policy.logFilePath(0)));

        String logContent = Files.readString(policy.logFilePath(0), StandardCharsets.UTF_8);
        assertTrue(logContent.contains("Zażółć gęślą jaźń"));
        assertTrue(consoleBuffer.toString(StandardCharsets.UTF_8).contains("Zażółć gęślą jaźń"));
    }

    @Test
    void shouldKeepInitializationIdempotentWithoutDuplicatingOutput() throws IOException {
        AgentLogPolicy policy = testPolicy(1024, 3);

        try (PrintStream console = console(new ByteArrayOutputStream())) {
            AgentLogging firstInitialization = AgentLogging.initialize(
                    policy,
                    console,
                    BoundedLogFileOutputStream::new
            );
            AgentLogging secondInitialization = AgentLogging.initialize(
                    policy,
                    console,
                    BoundedLogFileOutputStream::new
            );

            assertSame(firstInitialization, secondInitialization);

            System.err.println("single initialization message");
            System.err.flush();
        }

        AgentLogging.closeCurrent();

        assertEquals(1, countOccurrences(readAllLogFiles(policy.logDirectory()), "single initialization message"));
    }

    @Test
    void shouldFlushReleaseFileHandleAndAllowRepeatedClose() throws IOException {
        AgentLogPolicy policy = testPolicy(1024, 3);

        try (PrintStream console = console(new ByteArrayOutputStream())) {
            AgentLogging.initialize(policy, console, BoundedLogFileOutputStream::new);
            System.err.print("message flushed on close");
        }

        AgentLogging.closeCurrent();
        assertDoesNotThrow(AgentLogging::closeCurrent);

        String logContent = Files.readString(policy.logFilePath(0), StandardCharsets.UTF_8);
        assertTrue(logContent.contains("message flushed on close"));

        Path movedLog = tempDir.resolve("moved.log");
        Files.move(policy.logFilePath(0), movedLog);
        Files.delete(movedLog);
    }

    @Test
    void shouldRotateBySizeAndKeepConfiguredFileCount() throws IOException {
        AgentLogPolicy policy = testPolicy(1024, 3);

        try (PrintStream console = console(new ByteArrayOutputStream())) {
            AgentLogging.initialize(policy, console, BoundedLogFileOutputStream::new);

            for (int index = 0; index < 220; index++) {
                System.err.println("rotation-line-" + index + " " + "x".repeat(120));
            }

            System.err.flush();
        }

        AgentLogging.closeCurrent();

        List<Path> logFiles = listLogFiles(policy.logDirectory());
        String logContent = readAllLogFiles(policy.logDirectory());

        assertTrue(logFiles.size() <= 3);
        assertTrue(logFiles.size() > 1);
        assertTrue(logContent.contains("rotation-line-219"));
        assertFalse(logContent.contains("rotation-line-0 "));
        assertTrue(totalLogSize(logFiles) <= 3L * 1024);
    }

    @Test
    void shouldKeepConsoleAvailableWhenFileLoggingInitializationFails() {
        AgentLogPolicy policy = testPolicy(1024, 3);
        ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();

        try (PrintStream console = console(consoleBuffer)) {
            AgentLogging.initialize(policy, console, ignored -> {
                throw new IOException("simulated file handler failure");
            });

            assertFalse(AgentLogging.isFileLoggingEnabled());
            System.err.println("console remains available");
        }

        AgentLogging.closeCurrent();

        String consoleContent = consoleBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(consoleContent.contains("file logging could not be initialized"));
        assertTrue(consoleContent.contains("console remains available"));
    }

    @Test
    void shouldNotPersistApiKeyOrSensitiveHeaderNamesFromRepresentativeMessages() throws IOException {
        AgentLogPolicy policy = testPolicy(1024, 3);
        String apiKey = "test-api-key-should-never-appear";
        String mappedError = MasterClientErrorMapper.mapHttpError(
                "Heartbeat",
                401,
                """
                        {
                          "status": "error",
                          "message": "Rejected X-API-KEY: test-api-key-should-never-appear and Authorization: Bearer token-123"
                        }
                        """,
                JSON_MAPPER
        );

        try (PrintStream console = console(new ByteArrayOutputStream())) {
            AgentLogging.initialize(policy, console, BoundedLogFileOutputStream::new);

            System.err.println("API key configured: " + !apiKey.isBlank());
            System.err.println("Worker API ready: true");
            System.err.println(mappedError);
            System.err.flush();
        }

        AgentLogging.closeCurrent();

        String logContent = readAllLogFiles(policy.logDirectory());
        assertFalse(logContent.contains(apiKey));
        assertFalse(logContent.contains("X-API-KEY"));
        assertFalse(logContent.contains("Authorization: Bearer"));
        assertFalse(logContent.contains("token-123"));
    }

    private AgentLogPolicy testPolicy(int maxFileSizeBytes, int maxFileCount) {
        return new AgentLogPolicy(tempDir.resolve("logs"), maxFileSizeBytes, maxFileCount);
    }

    private static PrintStream console(ByteArrayOutputStream outputStream) {
        return new PrintStream(outputStream, true, StandardCharsets.UTF_8);
    }

    private static List<Path> listLogFiles(Path logDirectory) throws IOException {
        if (Files.notExists(logDirectory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(logDirectory)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith("localhive-agent."))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static String readAllLogFiles(Path logDirectory) throws IOException {
        StringBuilder builder = new StringBuilder();

        for (Path path : listLogFiles(logDirectory)) {
            builder.append(Files.readString(path, StandardCharsets.UTF_8));
        }

        return builder.toString();
    }

    private static long totalLogSize(List<Path> logFiles) throws IOException {
        long totalSize = 0;

        for (Path path : logFiles) {
            totalSize += Files.size(path);
        }

        return totalSize;
    }

    private static int countOccurrences(String value, String expectedText) {
        int count = 0;
        int index = value.indexOf(expectedText);

        while (index >= 0) {
            count++;
            index = value.indexOf(expectedText, index + expectedText.length());
        }

        return count;
    }
}
