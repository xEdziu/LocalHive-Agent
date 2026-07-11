package dev.adrian.goral.localhiveagent.state;

public enum WorkerMode {
    ACTIVE,
    PAUSED;

    public static WorkerMode fromPauseEnabled(boolean pauseEnabled) {
        return pauseEnabled ? PAUSED : ACTIVE;
    }
}
