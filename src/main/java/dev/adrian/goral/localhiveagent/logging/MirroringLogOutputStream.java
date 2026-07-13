package dev.adrian.goral.localhiveagent.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Objects;

final class MirroringLogOutputStream extends OutputStream {

    private final PrintStream consoleOutput;
    private final OutputStream fileOutput;

    private boolean fileOutputAvailable;
    private boolean fileFailureReported;
    private boolean fileOutputClosed;

    MirroringLogOutputStream(PrintStream consoleOutput, OutputStream fileOutput) {
        this.consoleOutput = Objects.requireNonNull(consoleOutput, "consoleOutput is required");
        this.fileOutput = Objects.requireNonNull(fileOutput, "fileOutput is required");
        this.fileOutputAvailable = true;
        this.fileFailureReported = false;
        this.fileOutputClosed = false;
    }

    @Override
    public synchronized void write(int value) throws IOException {
        consoleOutput.write(value);

        if (!fileOutputAvailable) {
            return;
        }

        try {
            fileOutput.write(value);
        } catch (IOException exception) {
            disableFileOutput(exception);
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
        consoleOutput.write(bytes, offset, length);

        if (!fileOutputAvailable) {
            return;
        }

        try {
            fileOutput.write(bytes, offset, length);
        } catch (IOException exception) {
            disableFileOutput(exception);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        consoleOutput.flush();

        if (!fileOutputAvailable) {
            return;
        }

        try {
            fileOutput.flush();
        } catch (IOException exception) {
            disableFileOutput(exception);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        consoleOutput.flush();

        if (fileOutputClosed) {
            return;
        }

        try {
            if (fileOutputAvailable) {
                fileOutput.flush();
            }
        } finally {
            fileOutput.close();
            fileOutputClosed = true;
            fileOutputAvailable = false;
        }
    }

    void closeFileOutputQuietly() {
        if (fileOutputClosed) {
            return;
        }

        try {
            fileOutput.close();
        } catch (IOException ignored) {
            // Nothing useful can be done here; the console fallback is already active.
        } finally {
            fileOutputClosed = true;
            fileOutputAvailable = false;
        }
    }

    private void disableFileOutput(IOException exception) {
        fileOutputAvailable = false;

        if (fileFailureReported) {
            return;
        }

        fileFailureReported = true;
        consoleOutput.println("LocalHive Agent file logging failed and was disabled: " + exception.getMessage());
        closeFileOutputQuietly();
    }
}
