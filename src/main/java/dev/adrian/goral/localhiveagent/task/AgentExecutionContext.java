package dev.adrian.goral.localhiveagent.task;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public record AgentExecutionContext(
        Clock clock,
        String masterBaseUrl,
        UUID workerId,
        String apiKey
) {

    public AgentExecutionContext {
        Objects.requireNonNull(clock, "clock is required");
        masterBaseUrl = masterBaseUrl == null ? "" : masterBaseUrl.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public AgentExecutionContext(Clock clock) {
        this(clock, "", null, "");
    }

    public static AgentExecutionContext system() {
        return new AgentExecutionContext(Clock.systemDefaultZone());
    }

    @Override
    public String toString() {
        return "AgentExecutionContext["
                + "clock=" + clock
                + ", masterBaseUrl=" + masterBaseUrl
                + ", workerId=" + workerId
                + ", apiKey=<redacted>"
                + ']';
    }
}
