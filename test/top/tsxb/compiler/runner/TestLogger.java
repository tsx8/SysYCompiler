package top.tsxb.compiler.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

    public void log(String fileName, Path path) {
        try {
            Files.copy(path, logDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to copy to log file '" + fileName + "' in " + logDirectory);
            e.printStackTrace(System.err);
        }
    }

    public void logRunError(String stage, ProcessExecutor.Command command, ProcessExecutor.ProcessResult result) {
        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("Stage: ").append(stage).append(lineSeparator);
        builder.append("Command: ").append(command.toCommandLineString()).append(lineSeparator);
        builder.append("Exit Code: ").append(result.exitCode()).append(lineSeparator);
        if (!result.stdout().isBlank()) {
            builder.append(lineSeparator).append("[STDOUT]").append(lineSeparator);
            builder.append(result.stdout());
            if (!result.stdout().endsWith(lineSeparator)) {
                builder.append(lineSeparator);
            }
        }
        if (!result.stderr().isBlank()) {
            builder.append(lineSeparator).append("[STDERR]").append(lineSeparator);
            builder.append(result.stderr());
            if (!result.stderr().endsWith(lineSeparator)) {
                builder.append(lineSeparator);
            }
        }
        log("run.log", builder.toString());
    }
}
