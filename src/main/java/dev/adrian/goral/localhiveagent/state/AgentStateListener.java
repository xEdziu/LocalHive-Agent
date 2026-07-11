package dev.adrian.goral.localhiveagent.state;

@FunctionalInterface
public interface AgentStateListener {

    void onStateChanged(AgentStateSnapshot snapshot);
}
