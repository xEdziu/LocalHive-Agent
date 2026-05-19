package dev.adrian.goral.localhiveagent.master.dto;

import java.util.UUID;

public record WorkerRegistrationResponse(
        String status,
        String message,
        UUID workerId
) {
}