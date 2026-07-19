package dev.adrian.goral.localhiveagent.task;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class OutputDirectoryScanner implements OutputArtifactScanner {

    public static final int MAX_OUTPUT_FILES = 100;
    public static final long MAX_SINGLE_OUTPUT_FILE_SIZE_BYTES = 50L * 1024L * 1024L;
    public static final long MAX_TOTAL_OUTPUT_SIZE_BYTES = 200L * 1024L * 1024L;
    public static final int MAX_RELATIVE_PATH_LENGTH = 1024;

    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:.*");

    private final int maxOutputFiles;
    private final long maxSingleFileSizeBytes;
    private final long maxTotalSizeBytes;
    private final int maxRelativePathLength;

    public OutputDirectoryScanner() {
        this(
                MAX_OUTPUT_FILES,
                MAX_SINGLE_OUTPUT_FILE_SIZE_BYTES,
                MAX_TOTAL_OUTPUT_SIZE_BYTES,
                MAX_RELATIVE_PATH_LENGTH
        );
    }

    OutputDirectoryScanner(int maxOutputFiles,
                           long maxSingleFileSizeBytes,
                           long maxTotalSizeBytes,
                           int maxRelativePathLength) {
        if (maxOutputFiles < 1) {
            throw new IllegalArgumentException("maxOutputFiles must be positive.");
        }
        if (maxSingleFileSizeBytes < 1) {
            throw new IllegalArgumentException("maxSingleFileSizeBytes must be positive.");
        }
        if (maxTotalSizeBytes < 1) {
            throw new IllegalArgumentException("maxTotalSizeBytes must be positive.");
        }
        if (maxRelativePathLength < 1) {
            throw new IllegalArgumentException("maxRelativePathLength must be positive.");
        }
        this.maxOutputFiles = maxOutputFiles;
        this.maxSingleFileSizeBytes = maxSingleFileSizeBytes;
        this.maxTotalSizeBytes = maxTotalSizeBytes;
        this.maxRelativePathLength = maxRelativePathLength;
    }

    @Override
    public List<OutputArtifactFile> scan(Path outputDirectory) {
        Path root = WorkspacePathGuard.normalize(outputDirectory, "outputDirectory");
        if (Files.isSymbolicLink(root)) {
            throw invalid("Output directory cannot be a symbolic link.");
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("Output directory is not a directory.");
        }

        try (var paths = Files.walk(root)) {
            ScanState state = new ScanState();
            List<OutputArtifactFile> files = paths
                    .filter(path -> !path.equals(root))
                    .map(path -> inspectPath(root, path, state))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(OutputArtifactFile::relativePath))
                    .toList();

            return List.copyOf(files);
        } catch (IOException exception) {
            throw new OutputDirectoryInvalidException("Failed to scan output directory.", exception);
        } catch (UncheckedIOException exception) {
            throw new OutputDirectoryInvalidException("Failed to scan output directory.", exception.getCause());
        }
    }

    private OutputArtifactFile inspectPath(Path root, Path path, ScanState state) {
        Path candidate = WorkspacePathGuard.normalize(path, "outputPath");
        ensureInside(root, candidate);

        if (Files.isSymbolicLink(candidate)) {
            throw invalid("Output directory contains a symbolic link.");
        }
        if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("Output directory contains an unsupported path type.");
        }

        String relativePath = normalizeRelativePath(root.relativize(candidate));
        long sizeBytes = size(candidate);
        if (sizeBytes > maxSingleFileSizeBytes) {
            throw invalid("Output artifact exceeds 50 MiB.");
        }

        state.files++;
        if (state.files > maxOutputFiles) {
            throw invalid("Output directory contains more than 100 files.");
        }
        state.totalBytes += sizeBytes;
        if (state.totalBytes > maxTotalSizeBytes) {
            throw invalid("Output artifacts exceed 200 MiB total.");
        }

        return new OutputArtifactFile(candidate, relativePath, sizeBytes);
    }

    private String normalizeRelativePath(Path relativePath) {
        String normalized = relativePath.toString().replace('\\', '/').trim();
        if (normalized.isBlank()) {
            throw invalid("Output artifact relative path cannot be blank.");
        }
        if (normalized.length() > maxRelativePathLength) {
            throw invalid("Output artifact relative path exceeds 1024 characters.");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw invalid("Output artifact relative path cannot contain a null byte.");
        }
        if (normalized.startsWith("/") || normalized.startsWith("\\")) {
            throw invalid("Output artifact relative path must be relative.");
        }
        if (WINDOWS_DRIVE_PATH.matcher(normalized).matches()) {
            throw invalid("Output artifact relative path cannot use a Windows drive path.");
        }

        for (String segment : normalized.split("/", -1)) {
            if (segment.isBlank()) {
                throw invalid("Output artifact relative path cannot contain blank segments.");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw invalid("Output artifact relative path cannot contain traversal segments.");
            }
        }

        return normalized;
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void ensureInside(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            throw invalid("Output artifact path escapes output directory.");
        }
    }

    private static OutputDirectoryInvalidException invalid(String message) {
        return new OutputDirectoryInvalidException(message);
    }

    private static final class ScanState {

        private int files;
        private long totalBytes;
    }
}
