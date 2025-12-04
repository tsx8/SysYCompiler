package top.tsxb.compiler.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ProcessExecutor {
    private static final long TIMEOUT_SECONDS = 5;
    private static final int MAX_OUTPUT_SIZE = 5 * 1024 * 1024;

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
                    writer.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to write to process stdin", e);
                }
            });
            var stdoutFuture = CompletableFuture.supplyAsync(() -> readStreamSafe(process.getInputStream()));
            var stderrFuture = CompletableFuture.supplyAsync(() -> readStreamSafe(process.getErrorStream()));
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdoutFuture.join();
                stderrFuture.join();
                throw new RuntimeException(
                    "Process timed out after " + TIMEOUT_SECONDS + " seconds: " + cmd.toCommandLineString());
            }

            return new ProcessResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to execute command: " + cmd.toCommandLineString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Process execution was interrupted" + cmd.toCommandLineString(), e);
        }
    }

    private String readStreamSafe(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192]; // 8KB buffer
            int n;
            while ((n = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, n);
                if (sb.length() > MAX_OUTPUT_SIZE) {
                    sb.append("\n[OUTPUT TRUNCATED - EXCEEDED MEMORY LIMIT]\n");
                    break;
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error reading stream: " + e.getMessage();
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
