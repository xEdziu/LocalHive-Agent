package dev.adrian.goral.localhiveagent.task;

import java.time.Duration;
import java.util.List;

public interface DockerCommandRunner {

    DockerCommandResult run(List<String> command, Duration timeout);
}
