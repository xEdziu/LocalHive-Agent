package dev.adrian.goral.localhiveagent.task.history;

import dev.adrian.goral.localhiveagent.task.AgentExecutorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskHistoryStoreTest {

    private static final UUID EXECUTION_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    private static final Instant BASE_TIME = Instant.parse("2026-07-17T12:00:00Z");

    @TempDir
    private Path tempDir;

    @Test
    void initializeCreatesDatabaseFileAndEmptyTable() {
        Path databasePath = historyPath();
        AgentTaskHistoryStore store = new AgentTaskHistoryStore(databasePath);

        store.initialize();

        assertTrue(Files.exists(databasePath));
        assertEquals(0, store.count());
        assertTrue(store.findLatest(10).isEmpty());
    }

    @Test
    void shouldRecordClaimedRunningSucceededLifecycle() {
        AgentTaskHistoryStore store = initializedStore();

        recordClaimed(store, EXECUTION_ID, BASE_TIME);
        store.recordRunning(EXECUTION_ID, BASE_TIME.plusMillis(50));
        store.recordSucceeded(EXECUTION_ID, BASE_TIME.plusMillis(150));

        AgentTaskHistoryEntry entry = store.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(EXECUTION_ID, entry.executionId());
        assertEquals("NO-OP smoke test", entry.displayName());
        assertEquals("NO-OP smoke test", entry.displayNameOrFallback());
        assertEquals(AgentExecutorRegistry.NO_OP_EXECUTOR_ID, entry.executorId());
        assertEquals(AgentExecutorRegistry.NO_OP_CONTRACT_VERSION, entry.executorContractVersion());
        assertEquals(AgentTaskHistoryStatus.SUCCEEDED, entry.status());
        assertEquals(BASE_TIME, entry.claimedAt());
        assertEquals(BASE_TIME.plusMillis(50), entry.startedAt());
        assertEquals(BASE_TIME.plusMillis(150), entry.completedAt());
        assertEquals(100, entry.durationMs());
        assertEquals("", entry.failureCode());
        assertEquals("", entry.failureMessage());
        assertEquals("", entry.lastError());
    }

    @Test
    void shouldStoreTrimmedDisplayNameWithoutSecretsOrConfiguration() {
        AgentTaskHistoryStore store = initializedStore();

        store.recordClaimed(
                EXECUTION_ID,
                "  Custom smoke task  ",
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                BASE_TIME
        );
        store.recordRunning(EXECUTION_ID, BASE_TIME.plusMillis(25));
        store.recordSucceeded(EXECUTION_ID, BASE_TIME.plusMillis(75));

        AgentTaskHistoryEntry entry = store.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals("Custom smoke task", entry.displayName());
        assertEquals("Custom smoke task", entry.displayNameOrFallback());
        assertEquals("Custom smoke task / SUCCEEDED / 50 ms", entry.summary());

        String text = entry.toString();
        assertFalse(text.contains("leaseToken"));
        assertFalse(text.contains("configuration"));
    }

    @Test
    void initializeMigratesLegacyDatabaseAndKeepsDisplayFallback() throws Exception {
        Path databasePath = historyPath();
        Files.createDirectories(databasePath.getParent());
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE agent_task_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        execution_id TEXT NOT NULL,
                        executor_id TEXT NOT NULL,
                        executor_contract_version INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        claimed_at TEXT,
                        started_at TEXT,
                        completed_at TEXT,
                        duration_ms INTEGER,
                        failure_code TEXT,
                        failure_message TEXT,
                        last_error TEXT,
                        created_at_local TEXT NOT NULL,
                        updated_at_local TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO agent_task_history (
                        execution_id,
                        executor_id,
                        executor_contract_version,
                        status,
                        claimed_at,
                        created_at_local,
                        updated_at_local
                    )
                    VALUES (
                        '223e4567-e89b-12d3-a456-426614174000',
                        'localhive.no-op',
                        1,
                        'CLAIMED',
                        '2026-07-17T12:00:00Z',
                        '2026-07-17T12:00:00Z',
                        '2026-07-17T12:00:00Z'
                    )
                    """);
        }
        AgentTaskHistoryStore store = new AgentTaskHistoryStore(databasePath);

        store.initialize();

        AgentTaskHistoryEntry entry = store.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(null, entry.displayName());
        assertEquals("NO-OP smoke test", entry.displayNameOrFallback());
        assertEquals("NO-OP smoke test / CLAIMED", entry.summary());
    }

    @Test
    void shouldRecordFailedAndRedactSensitiveText() {
        AgentTaskHistoryStore store = initializedStore();

        recordClaimed(store, EXECUTION_ID, BASE_TIME);
        store.recordRunning(EXECUTION_ID, BASE_TIME.plusMillis(25));
        store.recordFailed(
                EXECUTION_ID,
                "EXECUTOR_FAILED",
                "Rejected X-EXECUTION-LEASE: raw-lease-token and X-API-KEY: raw-api-key",
                BASE_TIME.plusMillis(75)
        );

        AgentTaskHistoryEntry entry = store.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(AgentTaskHistoryStatus.FAILED, entry.status());
        assertEquals("EXECUTOR_FAILED", entry.failureCode());
        assertFalse(entry.failureMessage().contains("X-EXECUTION-LEASE"));
        assertFalse(entry.failureMessage().contains("raw-lease-token"));
        assertFalse(entry.failureMessage().contains("X-API-KEY"));
        assertFalse(entry.failureMessage().contains("raw-api-key"));
        assertEquals(50, entry.durationMs());
    }

    @Test
    void shouldRecordErrorWithoutSecrets() {
        AgentTaskHistoryStore store = initializedStore();

        recordClaimed(store, EXECUTION_ID, BASE_TIME);
        store.recordError(EXECUTION_ID, "leaseToken=raw-lease-token apiKey=raw-api-key", BASE_TIME.plusSeconds(1));

        AgentTaskHistoryEntry entry = store.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(AgentTaskHistoryStatus.ERROR, entry.status());
        assertFalse(entry.lastError().contains("raw-lease-token"));
        assertFalse(entry.lastError().contains("raw-api-key"));
    }

    @Test
    void findLatestReturnsNewestFirstAndFindByExecutionIdWorks() {
        AgentTaskHistoryStore store = initializedStore();
        UUID olderExecutionId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");
        UUID newerExecutionId = UUID.fromString("423e4567-e89b-12d3-a456-426614174000");

        recordClaimed(store, olderExecutionId, BASE_TIME);
        recordClaimed(store, newerExecutionId, BASE_TIME.plusSeconds(1));

        List<AgentTaskHistoryEntry> latest = store.findLatest(10);

        assertEquals(2, store.count());
        assertEquals(newerExecutionId, latest.get(0).executionId());
        assertEquals(olderExecutionId, latest.get(1).executionId());
        assertTrue(store.findByExecutionId(newerExecutionId).isPresent());
        assertEquals(Optional.empty(), store.findByExecutionId(UUID.randomUUID()));
    }

    @Test
    void retentionKeepsLatestFiveHundredRecordsAndRemovesOldest() {
        AgentTaskHistoryStore store = initializedStore();
        UUID oldestExecutionId = null;
        UUID newestExecutionId = null;

        for (int index = 0; index < 501; index++) {
            UUID executionId = UUID.nameUUIDFromBytes(("execution-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (index == 0) {
                oldestExecutionId = executionId;
            }
            if (index == 500) {
                newestExecutionId = executionId;
            }
            recordClaimed(store, executionId, BASE_TIME.plusSeconds(index));
        }

        assertEquals(AgentTaskHistoryStore.MAX_RETAINED_TASKS, store.count());
        assertTrue(store.findByExecutionId(oldestExecutionId).isEmpty());
        assertTrue(store.findByExecutionId(newestExecutionId).isPresent());
        assertEquals(newestExecutionId, store.findLatest(1).getFirst().executionId());
    }

    @Test
    void duplicateExecutionIdUpdatesExistingRecordDeterministically() {
        AgentTaskHistoryStore store = initializedStore();

        recordClaimed(store, EXECUTION_ID, "localhive.no-op", BASE_TIME);
        recordClaimed(store, EXECUTION_ID, "localhive.reclaimed", BASE_TIME.plusSeconds(5));

        AgentTaskHistoryEntry entry = store.findByExecutionId(EXECUTION_ID).orElseThrow();
        assertEquals(1, store.count());
        assertEquals("localhive.reclaimed", entry.executorId());
        assertEquals(BASE_TIME.plusSeconds(5), entry.claimedAt());
    }

    @Test
    void entryToStringDoesNotExposeClaimSecrets() {
        AgentTaskHistoryStore store = initializedStore();

        store.recordClaimed(
                EXECUTION_ID,
                "NO-OP smoke test",
                AgentExecutorRegistry.NO_OP_EXECUTOR_ID,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                BASE_TIME
        );

        AgentTaskHistoryEntry entry = store.findByExecutionId(EXECUTION_ID).orElseThrow();
        String text = entry.toString();
        assertFalse(text.contains("leaseToken"));
        assertFalse(text.contains("configuration"));
    }

    private AgentTaskHistoryStore initializedStore() {
        AgentTaskHistoryStore store = new AgentTaskHistoryStore(historyPath());
        store.initialize();
        return store;
    }

    private Path historyPath() {
        return tempDir.resolve(".localhive-agent").resolve("task-history.sqlite");
    }

    private static void recordClaimed(AgentTaskHistoryStore store, UUID executionId, Instant claimedAt) {
        recordClaimed(store, executionId, AgentExecutorRegistry.NO_OP_EXECUTOR_ID, claimedAt);
    }

    private static void recordClaimed(AgentTaskHistoryStore store,
                                      UUID executionId,
                                      String executorId,
                                      Instant claimedAt) {
        store.recordClaimed(
                executionId,
                "NO-OP smoke test",
                executorId,
                AgentExecutorRegistry.NO_OP_CONTRACT_VERSION,
                claimedAt
        );
    }
}
