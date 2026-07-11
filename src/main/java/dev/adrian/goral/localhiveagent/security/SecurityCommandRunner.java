package dev.adrian.goral.localhiveagent.security;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class SecurityCommandRunner {

    private SecurityCommandRunner() {
    }

    static CommandResult execute(
            List<String> command,
            String standardInput,
            Duration timeout
    ) {
        Process process = null;

        try {
            process = new ProcessBuilder(command).start();

            writeStandardInput(process, standardInput);

            boolean completed = process.waitFor(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            if (!completed) {
                process.destroyForcibly();

                throw new CredentialException(
                        "Credential command timed out."
                );
            }

            String standardOutput = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            String standardError = new String(
                    process.getErrorStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            return new CommandResult(
                    process.exitValue(),
                    standardOutput,
                    standardError
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new CredentialException(
                    "Credential command was interrupted.",
                    exception
            );
        } catch (IOException exception) {
            throw new CredentialException(
                    "Failed to execute credential command.",
                    exception
            );
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void writeStandardInput(
            Process process,
            String standardInput
    ) throws IOException {
        try (OutputStream outputStream = process.getOutputStream()) {
            if (standardInput == null) {
                return;
            }

            outputStream.write(
                    standardInput.getBytes(StandardCharsets.UTF_8)
            );

            outputStream.flush();
        }
    }
}