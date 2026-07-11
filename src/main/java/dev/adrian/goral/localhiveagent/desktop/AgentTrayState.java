package dev.adrian.goral.localhiveagent.desktop;

public record AgentTrayState(
        boolean workerRegistered,
        boolean workerApiReady,
        boolean pauseEnabled,
        boolean heartbeatRunning
) {

    public String modeLabel() {
        if (!workerRegistered) {
            return "Mode: Unregistered";
        }

        return pauseEnabled ? "Mode: Paused" : "Mode: Active";
    }

    public String heartbeatLabel() {
        return heartbeatRunning ? "Heartbeat: Running" : "Heartbeat: Stopped";
    }

    public String workerModeActionLabel() {
        return pauseEnabled ? "Resume Worker" : "Pause Worker";
    }
}
