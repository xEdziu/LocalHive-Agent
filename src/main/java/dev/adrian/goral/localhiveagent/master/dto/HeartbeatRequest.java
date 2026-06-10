package dev.adrian.goral.localhiveagent.master.dto;

public record HeartbeatRequest(
        boolean pauseEnabled,
        int sharedRamMb
) {
}