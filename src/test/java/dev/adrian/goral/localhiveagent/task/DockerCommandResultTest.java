package dev.adrian.goral.localhiveagent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DockerCommandResultTest {

    @Test
    void shouldBoundStdoutAndStderr() {
        DockerCommandResult result = DockerCommandResult.completed(
                0,
                "o".repeat(DockerCommandResult.MAX_STDOUT_CHARS + 10),
                "e".repeat(DockerCommandResult.MAX_STDERR_CHARS + 10),
                25
        );

        assertEquals(DockerCommandResult.MAX_STDOUT_CHARS, result.stdout().length());
        assertEquals(DockerCommandResult.MAX_STDERR_CHARS, result.stderr().length());
    }

    @Test
    void shouldNotExposeOutputInToString() {
        DockerCommandResult result = DockerCommandResult.completed(1, "stdout-secret", "stderr-secret", 25);

        String text = result.toString();

        assertFalse(text.contains("stdout-secret"));
        assertFalse(text.contains("stderr-secret"));
    }
}
