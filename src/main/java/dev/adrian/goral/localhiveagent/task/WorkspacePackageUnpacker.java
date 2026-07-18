package dev.adrian.goral.localhiveagent.task;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public final class WorkspacePackageUnpacker {

    public static final long MAX_PACKAGE_SIZE_BYTES = 50L * 1024L * 1024L;
    public static final long MAX_UNPACKED_SIZE_BYTES = 200L * 1024L * 1024L;
    public static final int MAX_ENTRIES = 1000;

    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:.*");

    private final long maxPackageSizeBytes;
    private final long maxUnpackedSizeBytes;
    private final int maxEntries;

    public WorkspacePackageUnpacker() {
        this(MAX_PACKAGE_SIZE_BYTES, MAX_UNPACKED_SIZE_BYTES, MAX_ENTRIES);
    }

    WorkspacePackageUnpacker(long maxPackageSizeBytes, long maxUnpackedSizeBytes, int maxEntries) {
        if (maxPackageSizeBytes < 1) {
            throw new IllegalArgumentException("maxPackageSizeBytes must be positive.");
        }
        if (maxUnpackedSizeBytes < 1) {
            throw new IllegalArgumentException("maxUnpackedSizeBytes must be positive.");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive.");
        }
        this.maxPackageSizeBytes = maxPackageSizeBytes;
        this.maxUnpackedSizeBytes = maxUnpackedSizeBytes;
        this.maxEntries = maxEntries;
    }

    public void unpack(Path packageFile, Path workspaceDirectory) {
        Path packagePath = Objects.requireNonNull(packageFile, "packageFile is required")
                .toAbsolutePath()
                .normalize();
        Path workspaceRoot = Objects.requireNonNull(workspaceDirectory, "workspaceDirectory is required")
                .toAbsolutePath()
                .normalize();

        try {
            validatePackageSize(packagePath);
            WorkspacePathGuard.createDirectoriesUnder(workspaceRoot, workspaceRoot);
            unpackEntries(packagePath, workspaceRoot);
        } catch (WorkspacePackageInvalidException | WorkspaceUnpackException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw new WorkspacePackageInvalidException("Workspace package is not a valid ZIP file.", exception);
        } catch (IOException exception) {
            throw new WorkspaceUnpackException("Workspace package could not be unpacked.", exception);
        }
    }

    private void validatePackageSize(Path packagePath) throws IOException {
        if (Files.size(packagePath) > maxPackageSizeBytes) {
            throw new WorkspacePackageInvalidException("Workspace package exceeds 50 MB.");
        }
    }

    private void unpackEntries(Path packagePath, Path workspaceRoot) throws IOException {
        Set<Path> seenTargets = new HashSet<>();
        long unpackedBytes = 0;
        int entries = 0;

        try (InputStream input = Files.newInputStream(packagePath);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > maxEntries) {
                    throw new WorkspacePackageInvalidException("Workspace package contains too many entries.");
                }

                Path target = resolveSafeTarget(workspaceRoot, entry.getName(), seenTargets);
                if (entry.isDirectory()) {
                    createDirectorySafely(workspaceRoot, target);
                    zip.closeEntry();
                    continue;
                }

                Path parent = target.getParent();
                if (parent != null) {
                    createDirectorySafely(workspaceRoot, parent);
                }
                rejectExistingTarget(target);

                try (OutputStream output = Files.newOutputStream(
                        target,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                )) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        unpackedBytes += read;
                        if (unpackedBytes > maxUnpackedSizeBytes) {
                            throw new WorkspacePackageInvalidException("Workspace package unpacked size exceeds 200 MB.");
                        }
                        output.write(buffer, 0, read);
                    }
                } catch (WorkspacePackageInvalidException exception) {
                    deletePartialFile(target);
                    throw exception;
                }
                zip.closeEntry();
            }
        }
    }

    private Path resolveSafeTarget(Path workspaceRoot, String entryName, Set<Path> seenTargets) {
        validateEntryName(entryName);

        Path relative = Path.of(entryName.replace('\\', '/')).normalize();
        Path target = workspaceRoot.resolve(relative).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new WorkspacePackageInvalidException("Workspace package entry escapes workspace directory.");
        }
        if (!seenTargets.add(target)) {
            throw new WorkspacePackageInvalidException("Workspace package contains duplicate target paths.");
        }
        return target;
    }

    private static void validateEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new WorkspacePackageInvalidException("Workspace package entry name cannot be blank.");
        }
        if (entryName.indexOf('\0') >= 0) {
            throw new WorkspacePackageInvalidException("Workspace package entry name contains a null byte.");
        }
        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw new WorkspacePackageInvalidException("Workspace package entry path cannot be absolute.");
        }
        if (WINDOWS_DRIVE_PATH.matcher(entryName).matches()) {
            throw new WorkspacePackageInvalidException("Workspace package entry path cannot use a Windows drive.");
        }

        String normalizedSeparators = entryName.replace('\\', '/');
        String[] segments = normalizedSeparators.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            boolean trailingSlash = index == segments.length - 1 && segment.isEmpty();
            if (trailingSlash) {
                continue;
            }
            if (segment.isEmpty()) {
                throw new WorkspacePackageInvalidException("Workspace package entry path cannot contain empty segments.");
            }
            if ("..".equals(segment)) {
                throw new WorkspacePackageInvalidException("Workspace package entry path cannot contain parent traversal.");
            }
        }
    }

    private static void createDirectorySafely(Path workspaceRoot, Path directory) throws IOException {
        if (directory.equals(workspaceRoot)) {
            WorkspacePathGuard.createDirectoriesUnder(workspaceRoot, workspaceRoot);
            return;
        }

        ensureNoSymlinkInExistingParents(workspaceRoot, directory);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new WorkspacePackageInvalidException("Workspace package target directory is unsafe.");
            }
            return;
        }

        Files.createDirectories(directory);
        ensureNoSymlinkInExistingParents(workspaceRoot, directory);
        if (Files.isSymbolicLink(directory)) {
            throw new WorkspacePackageInvalidException("Workspace package target directory is unsafe.");
        }
    }

    private static void rejectExistingTarget(Path target) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspacePackageInvalidException("Workspace package target file already exists.");
        }
    }

    private static void ensureNoSymlinkInExistingParents(Path workspaceRoot, Path target) {
        Path parent = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) ? target : target.getParent();
        if (parent == null) {
            return;
        }

        Path relative;
        try {
            relative = workspaceRoot.relativize(parent);
        } catch (IllegalArgumentException exception) {
            throw new WorkspacePackageInvalidException("Workspace package target escapes workspace directory.", exception);
        }

        Path current = workspaceRoot;
        if (Files.isSymbolicLink(current)) {
            throw new WorkspacePackageInvalidException("Workspace package target directory is unsafe.");
        }
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new WorkspacePackageInvalidException("Workspace package target directory is unsafe.");
            }
        }
    }

    private static void deletePartialFile(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // The package is already invalid; partial cleanup is best effort.
        }
    }
}
