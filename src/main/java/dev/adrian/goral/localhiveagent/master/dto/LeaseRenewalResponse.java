package dev.adrian.goral.localhiveagent.master.dto;

import java.time.LocalDateTime;

public record LeaseRenewalResponse(
        Object leaseExpiresAt
) {

    public LocalDateTime leaseExpiresAtDateTime() {
        return MasterTimestampParser.parse(leaseExpiresAt, "leaseExpiresAt");
    }
}
