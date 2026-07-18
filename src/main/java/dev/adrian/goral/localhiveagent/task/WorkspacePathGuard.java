package dev.adrian.goral.localhiveagent.task;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

final class WorkspacePathGuard {

    private WorkspacePathGuard() {
    }

    static Path normalize(Path path, String fieldName) {
        return Objects.requireNonNull(path, fieldName + " is required")
                .toAbsolutePath()
                .normalize();
    }

    static void createDirectoriesUnder(Path trustedRoot, Path directory) throws IOException {
        Path root = normalize(trustedRoot, "trustedRoot");
        Path target = normalize(directory, "directory");
        ensureInside(root, target);

        createSingleDirectory(root);
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            createSingleDirectory(current);
        }
    }

    static void ensureNoExistingSymlinksUnder(Path trustedRoot, Path target, boolean targetMayBeFile) {
        Path root = normalize(trustedRoot, "trustedRoot");
        Path targetPath = normalize(target, "target");
        ensureInside(root, targetPath);

        inspectExistingPath(root, false, root.equals(targetPath) && targetMayBeFile);
        Path current = root;
        for (Path segment : root.relativize(targetPath)) {
            current = current.resolve(segment);
            inspectExistingPath(current, current.equals(targetPath), targetMayBeFile);
        }
    }

    private static void createSingleDirectory(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            inspectExistingPath(directory, true, false);
            return;
        }

        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException exception) {
            inspectExistingPath(directory, true, false);
        }
    }

    private static void inspectExistingPath(Path path, boolean isLast, boolean targetMayBeFile) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(path)) {
            throw new WorkspaceUnpackException("Workspace path contains a symbolic link.");
        }
        if (isLast && targetMayBeFile) {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new WorkspaceUnpackException("Workspace package target is not a file.");
            }
            return;
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceUnpackException("Workspace path parent is not a directory.");
        }
    }

    private static void ensureInside(Path trustedRoot, Path target) {
        if (!target.startsWith(trustedRoot)) {
            throw new WorkspaceUnpackException("Workspace path escapes Agent workspace root.");
        }
    }
}
