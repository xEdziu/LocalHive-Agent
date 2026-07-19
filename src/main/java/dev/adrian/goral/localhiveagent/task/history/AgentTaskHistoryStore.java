package dev.adrian.goral.localhiveagent.task.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class AgentTaskHistoryStore {

    public static final int MAX_RETAINED_TASKS = 500;
    public static final String RETENTION_QUERY = """
            DELETE FROM agent_task_history
            WHERE id NOT IN (
                SELECT id
                FROM agent_task_history
                ORDER BY created_at_local DESC, id DESC
                LIMIT 500
            )
            """;

    private static final Logger log = LoggerFactory.getLogger(AgentTaskHistoryStore.class);

    private static final String SCHEMA_SQL = """
            CREATE TABLE IF NOT EXISTS agent_task_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                execution_id TEXT NOT NULL,
                display_name TEXT,
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
            """;
    private static final String EXECUTION_ID_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_agent_task_history_execution_id
                ON agent_task_history(execution_id)
            """;
    private static final String CREATED_AT_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_agent_task_history_created_at_local
                ON agent_task_history(created_at_local)
            """;

    private final Path databasePath;
    private final Object lock = new Object();
    private volatile boolean initialized;

    public AgentTaskHistoryStore(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath is required");
    }

    public void initialize() {
        synchronized (lock) {
            if (initialized) {
                return;
            }

            try {
                Path parent = databasePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                loadSqliteDriver();
                try (Connection connection = openConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute(SCHEMA_SQL);
                    ensureDisplayNameColumn(connection);
                    statement.execute(EXECUTION_ID_INDEX_SQL);
                    statement.execute(CREATED_AT_INDEX_SQL);
                }

                initialized = true;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to initialize task history database.", exception);
            }
        }
    }

    public void recordClaimed(UUID executionId,
                              String displayName,
                              String executorId,
                              int executorContractVersion,
                              Instant claimedAt) {
        UUID validExecutionId = Objects.requireNonNull(executionId, "executionId is required");
        String normalizedDisplayName = normalizeDisplayName(displayName);
        String validExecutorId = requireNonBlank(executorId, "executorId");
        Instant timestamp = Objects.requireNonNull(claimedAt, "claimedAt is required");

        synchronized (lock) {
            initialize();
            try (Connection connection = openConnection()) {
                runInTransaction(connection, () -> {
                    int updated = updateClaimed(
                            connection,
                            validExecutionId,
                            normalizedDisplayName,
                            validExecutorId,
                            executorContractVersion,
                            timestamp
                    );
                    if (updated == 0) {
                        insertClaimed(
                                connection,
                                validExecutionId,
                                normalizedDisplayName,
                                validExecutorId,
                                executorContractVersion,
                                timestamp
                        );
                    }
                    enforceRetention(connection);
                });
            } catch (SQLException exception) {
                throw historyException("Failed to record claimed task history.", exception);
            }
        }
    }

    public void recordRunning(UUID executionId, Instant startedAt) {
        updateStatus(
                executionId,
                AgentTaskHistoryStatus.RUNNING,
                null,
                Objects.requireNonNull(startedAt, "startedAt is required"),
                null,
                null,
                null,
                null
        );
    }

    public void recordSucceeded(UUID executionId, Instant completedAt) {
        updateStatus(
                executionId,
                AgentTaskHistoryStatus.SUCCEEDED,
                null,
                null,
                Objects.requireNonNull(completedAt, "completedAt is required"),
                null,
                null,
                null
        );
    }

    public void recordFailed(UUID executionId, String failureCode, String failureMessage, Instant completedAt) {
        updateStatus(
                executionId,
                AgentTaskHistoryStatus.FAILED,
                null,
                null,
                Objects.requireNonNull(completedAt, "completedAt is required"),
                safeText(failureCode),
                safeText(failureMessage),
                null
        );
    }

    public void recordError(UUID executionId, String lastError, Instant updatedAt) {
        updateStatus(
                executionId,
                AgentTaskHistoryStatus.ERROR,
                Objects.requireNonNull(updatedAt, "updatedAt is required"),
                null,
                null,
                null,
                null,
                safeText(lastError)
        );
    }

    public List<AgentTaskHistoryEntry> findLatest(int limit) {
        if (limit < 1) {
            return List.of();
        }

        synchronized (lock) {
            initialize();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT *
                         FROM agent_task_history
                         ORDER BY created_at_local DESC, id DESC
                         LIMIT ?
                         """)) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<AgentTaskHistoryEntry> entries = new ArrayList<>();
                    while (resultSet.next()) {
                        entries.add(mapEntry(resultSet));
                    }
                    return entries;
                }
            } catch (SQLException exception) {
                throw historyException("Failed to read task history.", exception);
            }
        }
    }

    public Optional<AgentTaskHistoryEntry> findByExecutionId(UUID executionId) {
        Objects.requireNonNull(executionId, "executionId is required");

        synchronized (lock) {
            initialize();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT *
                         FROM agent_task_history
                         WHERE execution_id = ?
                         ORDER BY id DESC
                         LIMIT 1
                         """)) {
                statement.setString(1, executionId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapEntry(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw historyException("Failed to find task history entry.", exception);
            }
        }
    }

    public long count() {
        synchronized (lock) {
            initialize();
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM agent_task_history")) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            } catch (SQLException exception) {
                throw historyException("Failed to count task history.", exception);
            }
        }
    }

    public void enforceRetention() {
        synchronized (lock) {
            initialize();
            try (Connection connection = openConnection()) {
                runInTransaction(connection, () -> enforceRetention(connection));
            } catch (SQLException exception) {
                throw historyException("Failed to enforce task history retention.", exception);
            }
        }
    }

    public Path databasePath() {
        return databasePath;
    }

    private void updateStatus(UUID executionId,
                              AgentTaskHistoryStatus status,
                              Instant updatedAt,
                              Instant startedAt,
                              Instant completedAt,
                              String failureCode,
                              String failureMessage,
                              String lastError) {
        UUID validExecutionId = Objects.requireNonNull(executionId, "executionId is required");
        AgentTaskHistoryStatus validStatus = Objects.requireNonNull(status, "status is required");
        Instant timestamp = updatedAt == null
                ? firstPresent(completedAt, startedAt, Instant.now())
                : updatedAt;

        synchronized (lock) {
            initialize();
            try (Connection connection = openConnection()) {
                runInTransaction(connection, () -> {
                    AgentTaskHistoryEntry current = findByExecutionId(connection, validExecutionId).orElse(null);
                    if (current == null) {
                        log.warn("Task history entry not found for execution {}. Status update skipped.", validExecutionId);
                        return;
                    }

                    Long durationMs = completedAt == null ? current.durationMs() : durationMillis(current, completedAt);
                    updateExistingStatus(
                            connection,
                            validExecutionId,
                            validStatus,
                            startedAt,
                            completedAt,
                            durationMs,
                            failureCode,
                            failureMessage,
                            lastError,
                            timestamp
                    );
                    enforceRetention(connection);
                });
            } catch (SQLException exception) {
                throw historyException("Failed to update task history.", exception);
            }
        }
    }

    private int updateClaimed(Connection connection,
                              UUID executionId,
                              String displayName,
                              String executorId,
                              int executorContractVersion,
                              Instant timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE agent_task_history
                SET display_name = ?,
                    executor_id = ?,
                    executor_contract_version = ?,
                    status = ?,
                    claimed_at = ?,
                    started_at = NULL,
                    completed_at = NULL,
                    duration_ms = NULL,
                    failure_code = NULL,
                    failure_message = NULL,
                    last_error = NULL,
                    updated_at_local = ?
                WHERE execution_id = ?
                """)) {
            setNullableString(statement, 1, displayName);
            statement.setString(2, executorId);
            statement.setInt(3, executorContractVersion);
            statement.setString(4, AgentTaskHistoryStatus.CLAIMED.name());
            statement.setString(5, timestamp.toString());
            statement.setString(6, timestamp.toString());
            statement.setString(7, executionId.toString());
            return statement.executeUpdate();
        }
    }

    private void insertClaimed(Connection connection,
                               UUID executionId,
                               String displayName,
                               String executorId,
                               int executorContractVersion,
                               Instant timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_task_history (
                    execution_id,
                    display_name,
                    executor_id,
                    executor_contract_version,
                    status,
                    claimed_at,
                    created_at_local,
                    updated_at_local
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, executionId.toString());
            setNullableString(statement, 2, displayName);
            statement.setString(3, executorId);
            statement.setInt(4, executorContractVersion);
            statement.setString(5, AgentTaskHistoryStatus.CLAIMED.name());
            statement.setString(6, timestamp.toString());
            statement.setString(7, timestamp.toString());
            statement.setString(8, timestamp.toString());
            statement.executeUpdate();
        }
    }

    private void updateExistingStatus(Connection connection,
                                      UUID executionId,
                                      AgentTaskHistoryStatus status,
                                      Instant startedAt,
                                      Instant completedAt,
                                      Long durationMs,
                                      String failureCode,
                                      String failureMessage,
                                      String lastError,
                                      Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE agent_task_history
                SET status = ?,
                    started_at = COALESCE(?, started_at),
                    completed_at = COALESCE(?, completed_at),
                    duration_ms = ?,
                    failure_code = ?,
                    failure_message = ?,
                    last_error = ?,
                    updated_at_local = ?
                WHERE execution_id = ?
                """)) {
            statement.setString(1, status.name());
            setNullableString(statement, 2, startedAt == null ? null : startedAt.toString());
            setNullableString(statement, 3, completedAt == null ? null : completedAt.toString());
            setNullableLong(statement, 4, durationMs);
            setNullableString(statement, 5, failureCode);
            setNullableString(statement, 6, failureMessage);
            setNullableString(statement, 7, lastError);
            statement.setString(8, updatedAt.toString());
            statement.setString(9, executionId.toString());
            statement.executeUpdate();
        }
    }

    private Optional<AgentTaskHistoryEntry> findByExecutionId(Connection connection, UUID executionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM agent_task_history
                WHERE execution_id = ?
                ORDER BY id DESC
                LIMIT 1
                """)) {
            statement.setString(1, executionId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapEntry(resultSet)) : Optional.empty();
            }
        }
    }

    private void enforceRetention(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(RETENTION_QUERY);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
    }

    private static void runInTransaction(Connection connection, SqlRunnable runnable) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            runnable.run();
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static AgentTaskHistoryEntry mapEntry(ResultSet resultSet) throws SQLException {
        return new AgentTaskHistoryEntry(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("execution_id")),
                resultSet.getString("display_name"),
                resultSet.getString("executor_id"),
                resultSet.getInt("executor_contract_version"),
                AgentTaskHistoryStatus.valueOf(resultSet.getString("status")),
                parseInstant(resultSet.getString("claimed_at")),
                parseInstant(resultSet.getString("started_at")),
                parseInstant(resultSet.getString("completed_at")),
                nullableLong(resultSet, "duration_ms"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                resultSet.getString("last_error"),
                Instant.parse(resultSet.getString("created_at_local")),
                Instant.parse(resultSet.getString("updated_at_local"))
        );
    }

    private static Long durationMillis(AgentTaskHistoryEntry entry, Instant completedAt) {
        Instant start = firstPresent(entry.startedAt(), entry.claimedAt(), entry.createdAtLocal());
        return Math.max(0, Duration.between(start, completedAt).toMillis());
    }

    private static Instant firstPresent(Instant first, Instant second, Instant fallback) {
        if (first != null) {
            return first;
        }

        return second == null ? fallback : second;
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private static void setNullableString(PreparedStatement statement, int parameterIndex, String value)
            throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setString(parameterIndex, null);
            return;
        }

        statement.setString(parameterIndex, value);
    }

    private static void setNullableLong(PreparedStatement statement, int parameterIndex, Long value)
            throws SQLException {
        if (value == null) {
            statement.setObject(parameterIndex, null);
            return;
        }

        statement.setLong(parameterIndex, value);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }

        return value.trim();
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("displayName must be at most 255 characters.");
        }

        return trimmed;
    }

    private static String safeText(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .replaceAll("(?i)Authorization\\s*:\\s*Bearer\\s+\\S+", "[redacted authorization header]")
                .replaceAll("(?i)X-API-KEY\\s*[:=]\\s*\\S+", "[redacted api key header]")
                .replaceAll("(?i)X-API-KEY", "[redacted api key header]")
                .replaceAll("(?i)X-EXECUTION-LEASE\\s*[:=]\\s*\\S+", "[redacted execution lease header]")
                .replaceAll("(?i)X-EXECUTION-LEASE", "[redacted execution lease header]")
                .replaceAll("(?i)leaseToken\\s*[:=]\\s*\\S+", "leaseToken=<redacted>")
                .replaceAll("(?i)apiKey\\s*[:=]\\s*\\S+", "apiKey=<redacted>");
    }

    private static IllegalStateException historyException(String message, SQLException exception) {
        return new IllegalStateException(message, exception);
    }

    private static void loadSqliteDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    private static void ensureDisplayNameColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(agent_task_history)")) {
            while (resultSet.next()) {
                if ("display_name".equals(resultSet.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE agent_task_history ADD COLUMN display_name TEXT");
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }
}
