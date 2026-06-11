package dev.adrian.goral.localhiveagent.master.dto;

import java.util.Map;

public record MasterErrorResponse(
        String status,
        String message,
        Map<String, String> fieldErrors,
        String timestamp
) {
}