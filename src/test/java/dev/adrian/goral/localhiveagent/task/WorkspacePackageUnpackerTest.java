package dev.adrian.goral.localhiveagent.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePackageUnpackerTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldUnpackNormalWorkspacePackage() throws IOException {
        Path packageFile = zip(Map.of(
                "main.txt", "hello",
                "nested/config.txt", "config"
        ));
        Path workspace = tempDir.resolve("workspace");

        new WorkspacePackageUnpacker().unpack(packageFile, workspace);

        assertEquals("hello", Files.readString(workspace.resolve("main.txt")));
        assertEquals("config", Files.readString(workspace.resolve("nested").resolve("config.txt")));
    }

    @Test
    void shouldRejectParentTraversalEntry() throws IOException {
        Path packageFile = zip(Map.of("../evil.txt", "bad"));

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> new WorkspacePackageUnpacker().unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectAbsoluteEntryPath() throws IOException {
        Path packageFile = zip(Map.of("/absolute/path.txt", "bad"));

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> new WorkspacePackageUnpacker().unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectWindowsDrivePath() throws IOException {
        Path packageFile = zip(Map.of("C:\\evil.txt", "bad"));

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> new WorkspacePackageUnpacker().unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectBackslashTraversal() throws IOException {
        Path packageFile = zip(Map.of("dir\\..\\evil.txt", "bad"));

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> new WorkspacePackageUnpacker().unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectDuplicateTargetPaths() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("main.txt", "first");
        entries.put("./main.txt", "second");
        Path packageFile = zip(entries);

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> new WorkspacePackageUnpacker().unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectMoreFilesThanAllowed() throws IOException {
        Path packageFile = zip(Map.of(
                "one.txt", "1",
                "two.txt", "2"
        ));
        WorkspacePackageUnpacker unpacker = new WorkspacePackageUnpacker(1024, 1024, 1);

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> unpacker.unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectMoreThanMaxEntriesForDirectoryOnlyPackage() throws IOException {
        Path packageFile = zipDirectories(WorkspacePackageUnpacker.MAX_ENTRIES + 1);

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> new WorkspacePackageUnpacker().unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectUnpackedSizeOverLimitWithoutLargeFile() throws IOException {
        Path packageFile = zip(Map.of("main.txt", "123456789"));
        WorkspacePackageUnpacker unpacker = new WorkspacePackageUnpacker(1024, 8, 1000);

        assertThrows(
                WorkspacePackageInvalidException.class,
                () -> unpacker.unpack(packageFile, tempDir.resolve("workspace"))
        );
    }

    @Test
    void shouldRejectPackageFileOverLimitWithoutReadingZip() throws IOException {
        Path packageFile = zip(Map.of("main.txt", "123456789"));
        WorkspacePackageUnpacker unpacker = new WorkspacePackageUnpacker(8, 1024, 1000);

        WorkspacePackageInvalidException exception = assertThrows(
                WorkspacePackageInvalidException.class,
                () -> unpacker.unpack(packageFile, tempDir.resolve("workspace"))
        );

        assertTrue(exception.getMessage().contains("50 MB"));
    }

    private Path zip(Map<String, String> entries) throws IOException {
        Path packageFile = Files.createTempFile(tempDir, "workspace-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(packageFile))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return packageFile;
    }

    private Path zipDirectories(int entries) throws IOException {
        Path packageFile = Files.createTempFile(tempDir, "workspace-directories-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(packageFile))) {
            for (int index = 0; index < entries; index++) {
                zip.putNextEntry(new ZipEntry("dir-" + index + "/"));
                zip.closeEntry();
            }
        }
        return packageFile;
    }
}
