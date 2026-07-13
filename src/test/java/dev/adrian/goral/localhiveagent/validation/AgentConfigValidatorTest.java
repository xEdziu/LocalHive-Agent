package dev.adrian.goral.localhiveagent.validation;

import dev.adrian.goral.localhiveagent.config.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigValidatorTest {

    @Test
    void shouldAcceptWorkerApiReadyConfig() {
        assertDoesNotThrow(() -> AgentConfigValidator.validateWorkerApiReady(readyConfig(), true));
    }

    @Test
    void shouldRejectNullConfig() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> AgentConfigValidator.validateWorkerApiReady(null, true)
        );

        assertTrue(exception.getMessage().contains("config"));
    }

    @Test
    void shouldRejectMissingMasterUrl() {
        AgentConfig config = readyConfig().withMasterBaseUrl("");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> AgentConfigValidator.validateWorkerApiReady(config, true)
        );

        assertTrue(exception.getMessage().contains("Master base URL"));
    }

    @Test
    void shouldRejectMissingWorkerId() {
        AgentConfig config = readyConfig().withWorkerId(null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> AgentConfigValidator.validateWorkerApiReady(config, true)
        );

        assertTrue(exception.getMessage().contains("Worker ID"));
    }

    @Test
    void shouldRejectMissingApiKey() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> AgentConfigValidator.validateWorkerApiReady(readyConfig(), false)
        );

        assertTrue(exception.getMessage().contains("API key"));
    }

    @Test
    void shouldParseValidSharedRam() {
        assertEquals(4096, AgentConfigValidator.parseSharedRamMb("4096", 8192));
    }

    @Test
    void shouldTreatEmptySharedRamAsZero() {
        assertEquals(0, AgentConfigValidator.parseSharedRamMb("", 8192));
    }

    @Test
    void shouldTreatBlankSharedRamAsZero() {
        assertEquals(0, AgentConfigValidator.parseSharedRamMb("   ", 8192));
    }

    @Test
    void shouldParseZeroSharedRam() {
        assertEquals(0, AgentConfigValidator.parseSharedRamMb("0", 8192));
    }

    @Test
    void shouldParseMaximumSharedRam() {
        assertEquals(8192, AgentConfigValidator.parseSharedRamMb("8192", 8192));
    }

    @Test
    void shouldRejectNegativeSharedRam() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentConfigValidator.parseSharedRamMb("-1", 8192)
        );

        assertTrue(exception.getMessage().contains("negative"));
    }

    @Test
    void shouldRejectSharedRamGreaterThanTotalRam() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentConfigValidator.parseSharedRamMb("8193", 8192)
        );

        assertTrue(exception.getMessage().contains("greater than total RAM"));
    }

    @Test
    void shouldRejectNonNumericSharedRam() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentConfigValidator.parseSharedRamMb("eight", 8192)
        );

        assertTrue(exception.getMessage().contains("valid integer"));
    }

    @Test
    void shouldParseSharedRamWithSurroundingWhitespace() {
        assertEquals(4096, AgentConfigValidator.parseSharedRamMb(" 4096 ", 8192));
    }

    @Test
    void shouldRejectInvalidTotalRamBeforeParsingValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentConfigValidator.parseSharedRamMb("", 0)
        );

        assertTrue(exception.getMessage().contains("Total RAM"));
    }

    private static AgentConfig readyConfig() {
        return new AgentConfig(
                "http://localhost:8080",
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                4096,
                false
        );
    }
}
