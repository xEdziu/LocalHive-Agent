package dev.adrian.goral.localhiveagent.task;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class DockerCliAvailabilityChecker implements DockerAvailabilityChecker {

    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public boolean isDockerAvailable() {
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean completed = process.waitFor(CHECK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroy();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
