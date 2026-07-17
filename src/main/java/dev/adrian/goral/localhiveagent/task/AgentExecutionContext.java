package dev.adrian.goral.localhiveagent.task;

import java.time.Clock;
import java.util.Objects;

public record AgentExecutionContext(Clock clock) {

    public AgentExecutionContext {
        Objects.requireNonNull(clock, "clock is required");
    }

    public static AgentExecutionContext system() {
        return new AgentExecutionContext(Clock.systemDefaultZone());
    }
}
