package dev.adrian.goral.localhiveagent.task;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ProcessDockerCommandRunner implements DockerCommandRunner {

    private static final Duration FORCE_DESTROY_GRACE = Duration.ofSeconds(2);
    private static final int BUFFER_SIZE = 1024;

    @Override
    public DockerCommandResult run(List<String> command, Duration timeout) {
        List<String> commandArguments = List.copyOf(Objects.requireNonNull(command, "command is required"));
        Duration validTimeout = Objects.requireNonNull(timeout, "timeout is required");
        if (commandArguments.isEmpty()) {
            throw new IllegalArgumentException("Docker command cannot be empty.");
        }
        if (validTimeout.isNegative() || validTimeout.isZero()) {
            throw new IllegalArgumentException("Docker command timeout must be positive.");
        }

        long startedNanos = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(commandArguments).start();
        } catch (IOException exception) {
            return DockerCommandResult.failedToStart(exception.getMessage(), elapsedMillis(startedNanos));
        }

        StreamCollector stdout = new StreamCollector(process.getInputStream(), DockerCommandResult.MAX_STDOUT_CHARS);
        StreamCollector stderr = new StreamCollector(process.getErrorStream(), DockerCommandResult.MAX_STDERR_CHARS);
        Thread stdoutThread = collectorThread(stdout, "localhive-docker-stdout");
        Thread stderrThread = collectorThread(stderr, "localhive-docker-stderr");
        stdoutThread.start();
        stderrThread.start();

        try {
            boolean completed = process.waitFor(validTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                destroyProcess(process);
                joinCollectors(stdoutThread, stderrThread);
                return DockerCommandResult.timedOut(stdout.text(), stderr.text(), elapsedMillis(startedNanos));
            }

            joinCollectors(stdoutThread, stderrThread);
            return DockerCommandResult.completed(
                    process.exitValue(),
                    stdout.text(),
                    stderr.text(),
                    elapsedMillis(startedNanos)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            joinCollectors(stdoutThread, stderrThread);
            return DockerCommandResult.failedToStart("Docker command was interrupted.", elapsedMillis(startedNanos));
        }
    }

    private static Thread collectorThread(StreamCollector collector, String name) {
        Thread thread = new Thread(collector, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void destroyProcess(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(FORCE_DESTROY_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(FORCE_DESTROY_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static void joinCollectors(Thread stdoutThread, Thread stderrThread) {
        joinCollector(stdoutThread);
        joinCollector(stderrThread);
    }

    private static void joinCollector(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static final class StreamCollector implements Runnable {

        private final InputStream inputStream;
        private final BoundedTextBuffer output;

        private StreamCollector(InputStream inputStream, int maxChars) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream is required");
            this.output = new BoundedTextBuffer(maxChars);
        }

        @Override
        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream stream = inputStream) {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // Process streams can close during timeout destruction.
            }
        }

        private String text() {
            return output.text();
        }
    }

    private static final class BoundedTextBuffer {

        private final int maxChars;
        private final StringBuilder builder = new StringBuilder();

        private BoundedTextBuffer(int maxChars) {
            this.maxChars = maxChars;
        }

        private synchronized void append(String value) {
            if (value == null || value.isEmpty() || builder.length() >= maxChars) {
                return;
            }

            int remaining = maxChars - builder.length();
            builder.append(value, 0, Math.min(remaining, value.length()));
        }

        private synchronized String text() {
            return builder.toString().trim();
        }
    }
}
