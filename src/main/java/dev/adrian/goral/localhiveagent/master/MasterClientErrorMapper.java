package dev.adrian.goral.localhiveagent.master;

import dev.adrian.goral.localhiveagent.master.dto.MasterErrorResponse;
import tools.jackson.databind.json.JsonMapper;

public final class MasterClientErrorMapper {

    private MasterClientErrorMapper() {
    }

    public static String mapHttpError(
            String operation,
            int statusCode,
            String responseBody,
            JsonMapper jsonMapper
    ) {
        MasterErrorResponse errorResponse = tryReadErrorResponse(responseBody, jsonMapper);
        String backendMessage = errorResponse == null ? "" : safeText(errorResponse.message());

        String baseMessage = switch (statusCode) {
            case 400 -> "Master rejected the request because the agent sent invalid data.";
            case 401 -> "Authentication failed. API key is missing or invalid.";
            case 403 -> "Access denied. Worker may not be approved or the API key has no permission.";
            case 404 -> "Worker was not found on Master.";
            case 409 -> "Worker already exists or is in a conflict state.";
            case 423 -> "Master is locked. Complete the first-time setup on the Master.";
            case 429 -> "Master rate limit exceeded. Try again later.";
            case 500 -> "Master encountered an internal server error.";
            case 502 -> "Master gateway error.";
            case 503 -> "Master is temporarily unavailable.";
            case 504 -> "Master did not respond in time.";
            default -> {
                if (statusCode >= 400 && statusCode < 500) {
                    yield "Master rejected the request.";
                }

                if (statusCode >= 500) {
                    yield "Master failed to process the request.";
                }

                yield "Unexpected response from Master.";
            }
        };

        if (!backendMessage.isBlank()) {
            return operation + ": " + baseMessage + " Details: " + backendMessage;
        }

        return operation + ": " + baseMessage;
    }

    public static String mapConnectionError(String operation, Throwable exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return operation + ": Cannot connect to Master.";
        }

        String normalizedMessage = message.toLowerCase();

        if (normalizedMessage.contains("connection refused")) {
            return operation + ": Cannot connect to Master. Check if the Master is running and the URL is correct.";
        }

        if (normalizedMessage.contains("timed out") || normalizedMessage.contains("timeout")) {
            return operation + ": Master did not respond in time.";
        }

        if (normalizedMessage.contains("no route to host")) {
            return operation + ": Master host is unreachable from this network.";
        }

        if (normalizedMessage.contains("unknownhost")) {
            return operation + ": Master hostname could not be resolved.";
        }

        return operation + ": HTTP request failed. Details: " + message;
    }

    private static MasterErrorResponse tryReadErrorResponse(String responseBody, JsonMapper jsonMapper) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            return jsonMapper.readValue(responseBody, MasterErrorResponse.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}