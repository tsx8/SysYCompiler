package top.tsxb.compiler.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class ProcessExecutor {
    private static final long TIMEOUT_SECONDS = 10;

    public ProcessResult execute(Command cmd) {
        try {
            var commandList = new ArrayList<String>();
            commandList.add(cmd.executablePath());
            commandList.addAll(cmd.arguments());
            ProcessBuilder pb = new ProcessBuilder(commandList).directory(cmd.workingDirectory().toFile());
            Process process = pb.start();
            cmd.stdinContent().ifPresent(stdin -> {
                try (var writer = process.getOutputStream()) {
                    writer.write(stdin.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to write to process stdin", e);
                }
            });
            var stdoutFuture =
                process.inputReader(StandardCharsets.UTF_8).lines().collect(Collectors.joining(System.lineSeparator()));
            var stderrFuture =
                process.errorReader(StandardCharsets.UTF_8).lines().collect(Collectors.joining(System.lineSeparator()));
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeException(
                    "Process timed out after " + TIMEOUT_SECONDS + " seconds: " + cmd.toCommandLineString());
            }
            return new ProcessResult(process.exitValue(), stdoutFuture, stderrFuture);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to execute command: " + cmd.toCommandLineString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Process execution was interrupted" + cmd.toCommandLineString(), e);
        }
    }

    public record Command(String executablePath, List<String> arguments, Path workingDirectory,
        Optional<String> stdinContent) {
        public String toCommandLineString() {
            return executablePath + " " + String.join(" ", arguments);
        }
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {

    }
}
