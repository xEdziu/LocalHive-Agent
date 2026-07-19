package dev.adrian.goral.localhiveagent.task;

import java.util.UUID;

@FunctionalInterface
interface OutputDirectoryPreparer {

    PreparedOutputDirectory prepare(UUID executionId);
}
