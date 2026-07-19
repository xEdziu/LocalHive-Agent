package dev.adrian.goral.localhiveagent.task;

import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
interface OutputArtifactScanner {

    List<OutputArtifactFile> scan(Path outputDirectory);
}
