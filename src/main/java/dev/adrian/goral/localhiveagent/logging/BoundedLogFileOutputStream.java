package dev.adrian.goral.localhiveagent.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

final class BoundedLogFileOutputStream extends OutputStream {

    private final AgentLogPolicy policy;

    private OutputStream activeOutput;
    private long activeFileSizeBytes;
    private boolean closed;

    BoundedLogFileOutputStream(AgentLogPolicy policy) throws IOException {
        this.policy = Objects.requireNonNull(policy, "policy is required");

        Files.createDirectories(policy.logDirectory());
        deleteFilesOutsideRetentionLimit();
        openActiveFileForAppend();

        if (activeFileSizeBytes >= policy.maxFileSizeBytes()) {
            rotate();
        }
    }

    @Override
    public synchronized void write(int value) throws IOException {
        byte[] bytes = {(byte) value};
        write(bytes, 0, bytes.length);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        ensureOpen();

        int currentOffset = offset;
        int remainingLength = length;

        while (remainingLength > 0) {
            if (activeFileSizeBytes >= policy.maxFileSizeBytes()) {
                rotate();
            }

            int availableBytes = policy.maxFileSizeBytes() - (int) activeFileSizeBytes;
            int chunkLength = Math.min(remainingLength, availableBytes);

            if (chunkLength <= 0) {
                rotate();
                continue;
            }

            activeOutput.write(bytes, currentOffset, chunkLength);
            activeFileSizeBytes += chunkLength;
            currentOffset += chunkLength;
            remainingLength -= chunkLength;
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        if (closed || activeOutput == null) {
            return;
        }

        activeOutput.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;

        if (activeOutput != null) {
            activeOutput.flush();
            activeOutput.close();
            activeOutput = null;
        }
    }

    private void rotate() throws IOException {
        if (activeOutput != null) {
            activeOutput.flush();
            activeOutput.close();
            activeOutput = null;
        }

        if (policy.maxFileCount() == 1) {
            Files.deleteIfExists(policy.logFilePath(0));
            openActiveFileForOverwrite();
            return;
        }

        Files.deleteIfExists(policy.logFilePath(policy.maxFileCount() - 1));

        for (int index = policy.maxFileCount() - 1; index >= 1; index--) {
            Path source = policy.logFilePath(index - 1);
            Path target = policy.logFilePath(index);

            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        openActiveFileForOverwrite();
    }

    private void openActiveFileForAppend() throws IOException {
        Path activeLogFile = policy.logFilePath(0);

        activeOutput = Files.newOutputStream(
                activeLogFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        activeFileSizeBytes = Files.size(activeLogFile);
    }

    private void openActiveFileForOverwrite() throws IOException {
        Path activeLogFile = policy.logFilePath(0);

        activeOutput = Files.newOutputStream(
                activeLogFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
        activeFileSizeBytes = 0;
    }

    private void deleteFilesOutsideRetentionLimit() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(policy.logDirectory())) {
            for (Path path : stream) {
                Path fileNamePath = path.getFileName();

                if (fileNamePath == null) {
                    continue;
                }

                String fileName = fileNamePath.toString();

                if (policy.isManagedLogFile(fileName)
                        && policy.extractLogFileIndex(fileName) >= policy.maxFileCount()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Log file output stream is closed.");
        }
    }
}
