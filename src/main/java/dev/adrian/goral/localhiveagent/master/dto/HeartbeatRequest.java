package dev.adrian.goral.localhiveagent.master.dto;

public record HeartbeatRequest(
        boolean pauseEnabled,
        int sharedRamMb,
        AgentCapabilities capabilities
) {
    public HeartbeatRequest(boolean pauseEnabled, int sharedRamMb) {
        this(pauseEnabled, sharedRamMb, null);
    }
}
