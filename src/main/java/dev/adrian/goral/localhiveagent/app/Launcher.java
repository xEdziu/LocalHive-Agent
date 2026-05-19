package dev.adrian.goral.localhiveagent.app;

import javafx.application.Application;

public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(LocalHiveAgentApplication.class, args);
    }
}