package dev.adrian.goral.localhiveagent.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OutputDirectoryScannerTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldReturnEmptyListForEmptyOutputDirectory() throws IOException {
        Path output = outputDirectory();

        assertTrue(new OutputDirectoryScanner().scan(output).isEmpty());
    }

    @Test
    void shouldScanRegularFilesRecursivelyWithDeterministicOrdering() throws IOException {
        Path output = outputDirectory();
        Files.createDirectories(output.resolve("dir"));
        Files.writeString(output.resolve("z.txt"), "z");
        Files.writeString(output.resolve("dir").resolve("a.txt"), "a");
        Files.createDirectories(output.resolve("empty-dir"));

        List<OutputArtifactFile> files = new OutputDirectoryScanner().scan(output);

        assertEquals(2, files.size());
        assertEquals("dir/a.txt", files.get(0).relativePath());
        assertEquals("z.txt", files.get(1).relativePath());
        assertEquals(1L, files.get(0).sizeBytes());
    }

    @Test
    void shouldRejectSymlinkedOutputRoot() throws IOException {
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Path link = tempDir.resolve("output-link");
        assumeTrue(createDirectorySymlink(link, outside), "Directory symlinks are not available.");

        assertThrows(OutputDirectoryInvalidException.class, () -> new OutputDirectoryScanner().scan(link));
    }

    @Test
    void shouldRejectSymlinkedOutputFile() throws IOException {
        Path output = outputDirectory();
        Path target = tempDir.resolve("target.txt");
        Files.writeString(target, "secret");
        Path link = output.resolve("link.txt");
        assumeTrue(createFileSymlink(link, target), "File symlinks are not available.");

        assertThrows(OutputDirectoryInvalidException.class, () -> new OutputDirectoryScanner().scan(output));
    }

    @Test
    void shouldRejectSymlinkedOutputDirectory() throws IOException {
        Path output = outputDirectory();
        Path target = tempDir.resolve("target-dir");
        Files.createDirectories(target);
        Path link = output.resolve("linked-dir");
        assumeTrue(createDirectorySymlink(link, target), "Directory symlinks are not available.");

        assertThrows(OutputDirectoryInvalidException.class, () -> new OutputDirectoryScanner().scan(output));
    }

    @Test
    void shouldRejectMoreThanMaximumOutputFiles() throws IOException {
        Path output = outputDirectory();
        for (int index = 0; index <= OutputDirectoryScanner.MAX_OUTPUT_FILES; index++) {
            Files.writeString(output.resolve("file-" + index + ".txt"), "x");
        }

        assertThrows(OutputDirectoryInvalidException.class, () -> new OutputDirectoryScanner().scan(output));
    }

    @Test
    void shouldRejectSingleFileOverLimitWithoutLargeFile() throws IOException {
        Path output = outputDirectory();
        Files.writeString(output.resolve("large.txt"), "123456789");
        OutputDirectoryScanner scanner = new OutputDirectoryScanner(100, 8, 200, 1024);

        assertThrows(OutputDirectoryInvalidException.class, () -> scanner.scan(output));
    }

    @Test
    void shouldRejectTotalSizeOverLimitWithoutLargeFiles() throws IOException {
        Path output = outputDirectory();
        Files.writeString(output.resolve("one.txt"), "12345");
        Files.writeString(output.resolve("two.txt"), "67890");
        OutputDirectoryScanner scanner = new OutputDirectoryScanner(100, 50, 8, 1024);

        assertThrows(OutputDirectoryInvalidException.class, () -> scanner.scan(output));
    }

    @Test
    void shouldRejectRelativePathOverLimit() throws IOException {
        Path output = outputDirectory();
        Files.writeString(output.resolve("long-name.txt"), "x");
        OutputDirectoryScanner scanner = new OutputDirectoryScanner(100, 50, 200, 8);

        assertThrows(OutputDirectoryInvalidException.class, () -> scanner.scan(output));
    }

    private Path outputDirectory() throws IOException {
        Path output = tempDir.resolve("output");
        Files.createDirectories(output);
        return output;
    }

    private static boolean createFileSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static boolean createDirectorySymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }
}
