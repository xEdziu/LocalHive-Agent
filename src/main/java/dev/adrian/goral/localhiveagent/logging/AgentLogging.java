package dev.adrian.goral.localhiveagent.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentLogging implements AutoCloseable {

    private static final Object LOCK = new Object();
    private static final String SIMPLE_LOGGER_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    private static AgentLogging activeLogging;

    private final PrintStream previousSystemErr;
    private final PrintStream installedSystemErr;
    private final OutputStream fileOutput;
    private final AtomicBoolean closed;
    private final boolean fileLoggingEnabled;

    private AgentLogging(
            PrintStream previousSystemErr,
            PrintStream installedSystemErr,
            OutputStream fileOutput,
            boolean fileLoggingEnabled
    ) {
        this.previousSystemErr = Objects.requireNonNull(previousSystemErr, "previousSystemErr is required");
        this.installedSystemErr = installedSystemErr;
        this.fileOutput = fileOutput;
        this.fileLoggingEnabled = fileLoggingEnabled;
        this.closed = new AtomicBoolean(false);
    }

    public static AgentLogging initializeDefault() {
        return initialize(AgentLogPolicy.defaultPolicy());
    }

    public static AgentLogging initialize(AgentLogPolicy policy) {
        return initialize(policy, System.err, BoundedLogFileOutputStream::new);
    }

    static AgentLogging initialize(
            AgentLogPolicy policy,
            PrintStream consoleOutput,
            LogFileOutputFactory fileOutputFactory
    ) {
        Objects.requireNonNull(policy, "policy is required");
        Objects.requireNonNull(consoleOutput, "consoleOutput is required");
        Objects.requireNonNull(fileOutputFactory, "fileOutputFactory is required");

        synchronized (LOCK) {
            if (activeLogging != null && !activeLogging.closed.get()) {
                return activeLogging;
            }

            configureSimpleLoggerProperties();

            PrintStream previousSystemErr = System.err;

            try {
                OutputStream fileOutput = fileOutputFactory.create(policy);
                MirroringLogOutputStream mirroringOutput = new MirroringLogOutputStream(consoleOutput, fileOutput);
                PrintStream installedSystemErr = new PrintStream(
                        mirroringOutput,
                        true,
                        StandardCharsets.UTF_8
                );

                System.setErr(installedSystemErr);

                activeLogging = new AgentLogging(
                        previousSystemErr,
                        installedSystemErr,
                        fileOutput,
                        true
                );
                return activeLogging;
            } catch (IOException | RuntimeException exception) {
                consoleOutput.println("LocalHive Agent file logging could not be initialized: "
                        + exception.getMessage());
                exception.printStackTrace(consoleOutput);
                System.setErr(consoleOutput);

                activeLogging = new AgentLogging(
                        previousSystemErr,
                        consoleOutput,
                        null,
                        false
                );
                return activeLogging;
            }
        }
    }

    public static boolean isFileLoggingEnabled() {
        synchronized (LOCK) {
            return activeLogging != null
                    && !activeLogging.closed.get()
                    && activeLogging.fileLoggingEnabled;
        }
    }

    public static void closeCurrent() {
        AgentLogging currentLogging;

        synchronized (LOCK) {
            currentLogging = activeLogging;
        }

        if (currentLogging != null) {
            currentLogging.close();
        }
    }

    @Override
    public void close() {
        synchronized (LOCK) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            try {
                if (installedSystemErr != null) {
                    installedSystemErr.flush();
                }

                if (fileOutput != null) {
                    try {
                        fileOutput.flush();
                    } finally {
                        fileOutput.close();
                    }
                }
            } catch (IOException exception) {
                previousSystemErr.println("LocalHive Agent file logging could not be closed cleanly: "
                        + exception.getMessage());
            } finally {
                if (System.err == installedSystemErr) {
                    System.setErr(previousSystemErr);
                }

                if (activeLogging == this) {
                    activeLogging = null;
                }
            }
        }
    }

    private static void configureSimpleLoggerProperties() {
        setPropertyIfMissing("org.slf4j.simpleLogger.defaultLogLevel", "info");
        setPropertyIfMissing("org.slf4j.simpleLogger.showDateTime", "true");
        setPropertyIfMissing("org.slf4j.simpleLogger.dateTimeFormat", SIMPLE_LOGGER_DATE_FORMAT);
        setPropertyIfMissing("org.slf4j.simpleLogger.showThreadName", "true");
        setPropertyIfMissing("org.slf4j.simpleLogger.showShortLogName", "true");
        setPropertyIfMissing("org.slf4j.simpleLogger.levelInBrackets", "true");
    }

    private static void setPropertyIfMissing(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    @FunctionalInterface
    interface LogFileOutputFactory {

        OutputStream create(AgentLogPolicy policy) throws IOException;
    }
}
