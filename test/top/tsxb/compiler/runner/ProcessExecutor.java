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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ProcessExecutor {
    private static final long TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_SIZE = 5 * 1024 * 1024;

    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ProcessExecutor-IO");
        t.setDaemon(true);
        return t;
    });

    public ProcessResult execute(Command cmd) {
        Process process = null;
        try {
            var commandList = new ArrayList<String>();
            commandList.add(cmd.executablePath());
            commandList.addAll(cmd.arguments());
            ProcessBuilder pb = new ProcessBuilder(commandList).directory(cmd.workingDirectory().toFile());
            process = pb.start();

            final Process p = process;
            if (cmd.stdinContent().isPresent()) {
                try (var writer = p.getOutputStream()) {
                    writer.write(cmd.stdinContent().get().getBytes(StandardCharsets.UTF_8));
                    writer.flush();
                }
            } else {
                p.getOutputStream().close();
            }

            var stdoutFuture = CompletableFuture.supplyAsync(() -> readStreamSafe(p.getInputStream()), IO_EXECUTOR);
            var stderrFuture = CompletableFuture.supplyAsync(() -> readStreamSafe(p.getErrorStream()), IO_EXECUTOR);

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                cleanupProcess(process);
                throw new RuntimeException(
                    "Process timed out after " + TIMEOUT_SECONDS + " seconds: " + cmd.toCommandLineString());
            }

            return new ProcessResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
        } catch (IOException e) {
            cleanupProcess(process);
            throw new UncheckedIOException("Failed to execute command: " + cmd.toCommandLineString(), e);
        } catch (InterruptedException e) {
            cleanupProcess(process);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Process execution was interrupted: " + cmd.toCommandLineString(), e);
        } catch (Throwable t) {
            cleanupProcess(process);
            throw t;
        }
    }

    private void cleanupProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
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
