package top.tsxb.compiler.common;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import top.tsxb.compiler.driver.CompilerConfig;
import top.tsxb.compiler.driver.Pipeline;

public class Runner {
    private static final Path SOURCE_PATH = Paths.get("testfile.txt");
    private static final Path INPUT_PATH = Paths.get("in.txt");
    private static final Path OUTPUT_PATH = Paths.get("out.txt");
    private static final Path IR_PATH = Paths.get("llvm_ir.txt");
    private static final Path LINKED_IR_PATH = Paths.get("out.ll");
    private static final Path LIB_SRC = CompilerConfig.LIBSYSY_DIR.resolve("libsysy.c");
    private static final Path LIB_IR = CompilerConfig.LIBSYSY_DIR.resolve("lib.ll");

    public static void main(String[] args) {
        try {
            new Runner().run();
        } catch (Exception e) {
            System.err.println("Runner Execution Failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run() throws Exception {
        System.out.println("[Runner] Initializing...");
        prepareLibrary();

        System.out.println("[Runner] Compiling source: " + SOURCE_PATH.toAbsolutePath());
        if (!Files.exists(SOURCE_PATH)) {
            throw new FileNotFoundException("Source file not found: " + SOURCE_PATH.toAbsolutePath());
        }

        String sourceCode = Files.readString(SOURCE_PATH);
        Pipeline pipeline = new Pipeline();

        String irCode = pipeline.run(sourceCode, "llvm");

        Files.writeString(IR_PATH, irCode);
        System.out.println("[Runner] IR generated at: " + IR_PATH.toAbsolutePath());

        String inputContent = "";
        if (Files.exists(INPUT_PATH)) {
            inputContent = Files.readString(INPUT_PATH);
            System.out.println("[Runner] Input loaded from: " + INPUT_PATH.toAbsolutePath());
        }

        String output = runLlvm(inputContent);

        Files.writeString(OUTPUT_PATH, output);
        System.out.println("[Runner] Output written to: " + OUTPUT_PATH.toAbsolutePath());
        System.out.println("================ PROGRAM OUTPUT ================");
        System.out.println(output);
        System.out.println("================================================");
    }

    private void prepareLibrary() throws IOException {
        if (!Files.exists(LIB_IR)) {
            System.out.println("[Runner] Compiling runtime library (libsysy)...");
            if (!Files.exists(LIB_SRC)) {
                throw new FileNotFoundException("Library source not found: " + LIB_SRC.toAbsolutePath());
            }
            exec(new String[] {CompilerConfig.CLANG_PATH, "-S", "-emit-llvm", LIB_SRC.toString(), "-o",
                LIB_IR.toString()}, null);
        }
    }

    private String runLlvm(String stdin) throws IOException {
        System.out.println("[Runner] Linking IR with Library...");
        exec(new String[] {CompilerConfig.LLVM_LINK_PATH, IR_PATH.toString(), LIB_IR.toString(), "-S", "-o",
            LINKED_IR_PATH.toString()}, null);

        System.out.println("[Runner] Executing with LLI...");
        return exec(new String[] {CompilerConfig.LLI_PATH, LINKED_IR_PATH.toString()}, stdin);
    }

    private String exec(String[] cmd, String input) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();

        if (input != null && !input.isEmpty()) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } else {
            p.getOutputStream().close();
        }

        // Use CompletableFuture to read streams asynchronously to avoid blocking before waitFor
        var stdoutFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
        var stderrFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });

        try {
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                throw new IOException("Process timed out: " + String.join(" ", cmd));
            }

            int exitCode = p.exitValue();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();

            if (exitCode != 0) {
                String errorMsg = "Command failed (Exit " + exitCode + "): " + String.join(" ", cmd);
                if (!stderr.isBlank()) {
                    errorMsg += "\nSTDERR:\n" + stderr;
                }
                throw new IOException(errorMsg);
            }

            return stdout;
        } catch (InterruptedException e) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Process interrupted", e);
        }
    }
}
