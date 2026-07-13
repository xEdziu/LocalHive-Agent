package dev.adrian.goral.localhiveagent.master;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterClientErrorMapperTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String OPERATION = "Heartbeat";

    @Test
    void shouldMapKnownHttpStatuses() {
        assertHttpMessageContains(400, "invalid data");
        assertHttpMessageContains(401, "Authentication failed");
        assertHttpMessageContains(403, "Access denied");
        assertHttpMessageContains(404, "Worker was not found");
        assertHttpMessageContains(409, "conflict");
        assertHttpMessageContains(423, "Master is locked");
        assertHttpMessageContains(429, "rate limit");
        assertHttpMessageContains(500, "internal server error");
        assertHttpMessageContains(502, "gateway error");
        assertHttpMessageContains(503, "temporarily unavailable");
        assertHttpMessageContains(504, "did not respond");
    }

    @Test
    void shouldMapUnknownHttpStatuses() {
        assertHttpMessageContains(418, "Master rejected the request");
        assertHttpMessageContains(599, "Master failed to process the request");
        assertHttpMessageContains(302, "Unexpected response from Master");
    }

    @Test
    void shouldAppendBackendMessageWhenAvailable() {
        String message = MasterClientErrorMapper.mapHttpError(
                OPERATION,
                400,
                """
                        {
                          "status": "error",
                          "message": "sharedRamMb must be less than totalRamMb"
                        }
                        """,
                JSON_MAPPER
        );

        assertTrue(message.contains("Details: sharedRamMb must be less than totalRamMb"));
    }

    @Test
    void shouldHandleEmptyResponseBody() {
        String message = MasterClientErrorMapper.mapHttpError(OPERATION, 400, "", JSON_MAPPER);

        assertTrue(message.contains("invalid data"));
        assertFalse(message.contains("Details:"));
    }

    @Test
    void shouldHandleMalformedJsonWithoutReturningTechnicalBody() {
        String message = MasterClientErrorMapper.mapHttpError(
                OPERATION,
                500,
                "{ stacktrace: secret internal details",
                JSON_MAPPER
        );

        assertTrue(message.contains("internal server error"));
        assertFalse(message.contains("stacktrace"));
        assertFalse(message.contains("secret internal details"));
    }

    @Test
    void shouldHandleNullResponseBody() {
        String message = MasterClientErrorMapper.mapHttpError(OPERATION, 503, null, JSON_MAPPER);

        assertTrue(message.contains("temporarily unavailable"));
        assertFalse(message.contains("Details:"));
    }

    @Test
    void shouldMapConnectionRefused() {
        String message = MasterClientErrorMapper.mapConnectionError(
                OPERATION,
                new IOException("Connection refused")
        );

        assertTrue(message.contains("Cannot connect to Master"));
    }

    @Test
    void shouldMapTimeout() {
        String message = MasterClientErrorMapper.mapConnectionError(
                OPERATION,
                new IOException("timeout")
        );

        assertTrue(message.contains("did not respond in time"));
    }

    @Test
    void shouldMapTimedOut() {
        String message = MasterClientErrorMapper.mapConnectionError(
                OPERATION,
                new IOException("request timed out")
        );

        assertTrue(message.contains("did not respond in time"));
    }

    @Test
    void shouldMapNoRouteToHost() {
        String message = MasterClientErrorMapper.mapConnectionError(
                OPERATION,
                new IOException("No route to host")
        );

        assertTrue(message.contains("host is unreachable"));
    }

    @Test
    void shouldMapUnknownHost() {
        String message = MasterClientErrorMapper.mapConnectionError(
                OPERATION,
                new IOException("java.net.UnknownHostException: master.local")
        );

        assertTrue(message.contains("hostname could not be resolved"));
    }

    @Test
    void shouldMapMissingExceptionMessage() {
        String message = MasterClientErrorMapper.mapConnectionError(
                OPERATION,
                new IOException((String) null)
        );

        assertTrue(message.contains("Cannot connect to Master"));
    }

    @Test
    void shouldMapGenericIOExceptionMessage() {
        String message = MasterClientErrorMapper.mapConnectionError(
                OPERATION,
                new IOException("network adapter is unavailable")
        );

        assertTrue(message.contains("HTTP request failed"));
        assertTrue(message.contains("network adapter is unavailable"));
    }

    private static void assertHttpMessageContains(int statusCode, String expectedText) {
        String message = MasterClientErrorMapper.mapHttpError(OPERATION, statusCode, null, JSON_MAPPER);

        assertTrue(
                message.contains(expectedText),
                () -> "Expected message for status " + statusCode + " to contain: " + expectedText
                        + ", actual: " + message
        );
    }
}
