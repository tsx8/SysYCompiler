package top.tsxb.compiler.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record TestLogger(Path logDirectory) {
    public TestLogger(Path logDirectory) {
        this.logDirectory = logDirectory;
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create log directory: " + logDirectory, e);
        }
    }

    public void log(String fileName, String content) {
        try {
            Files.writeString(logDirectory.resolve(fileName), content);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to write log file '" + fileName + "' in " + logDirectory);
            e.printStackTrace(System.err);
        }
    }
}
