package top.tsxb.compiler.strategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import top.tsxb.compiler.CompilerTest;
import top.tsxb.compiler.driver.Pipeline;
import top.tsxb.compiler.model.TestCase;
import top.tsxb.compiler.model.TestResult;
import top.tsxb.compiler.runner.ProcessExecutor;
import top.tsxb.compiler.runner.TestLogger;

public class IrExecutionStrategy implements TestStrategy {
    private final ProcessExecutor executor;
    private Path precompiledLibIr;

    public IrExecutionStrategy(ProcessExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void prepare() throws IOException {
        System.out.println("Prepareing SysY runtime library...");
        Path libDir = CompilerTest.LIBSYSY_DIR;
        Path libSource = libDir.resolve("libsysy.c").toAbsolutePath();
        this.precompiledLibIr = libDir.resolve("lib.ll").toAbsolutePath();
        var command = new ProcessExecutor.Command(CompilerTest.CLANG_PATH,
            List.of("-S", "-emit-llvm", libSource.toString(), "-o", precompiledLibIr.toString()), libDir,
            Optional.empty());
        var result = executor.execute(command);
        if (result.exitCode() != 0) {
            throw new IOException("Failed to compile libsysy.c:\n" + result.stderr());
        }
        System.out.println("Runtime library compiled successfully to: " + precompiledLibIr);
    }

    @Override
    public TestResult execute(TestCase testCase, TestLogger logger) {
        Pipeline pipeline = new Pipeline();
        Path tmpDir;
        try {
            tmpDir = Files.createTempDirectory("sysy_test_" + testCase.name());
        } catch (IOException e) {
            return new TestResult.ExecutionError("Failed to create temp directory", "", e.getMessage());
        }
        try {
            String stdinContent = Files.exists(testCase.inputFile()) ? Files.readString(testCase.inputFile()) : "";
            logger.log("input.txt", stdinContent);
            String sourceCode = Files.readString(testCase.sourceFile());
            logger.log("testfile.txt", sourceCode);
            String mainIr = pipeline.run(sourceCode, "llvm");
            logger.log("generated.ll", mainIr);
            Files.writeString(tmpDir.resolve("main.ll"), mainIr);
            Files.copy(precompiledLibIr, tmpDir.resolve("lib.ll"), StandardCopyOption.REPLACE_EXISTING);
            var linkCommand = new ProcessExecutor.Command(CompilerTest.LLVM_LINK_PATH,
                List.of("main.ll", "lib.ll", "-S", "-o", "out.ll"), tmpDir, Optional.empty());
            var linkResult = executor.execute(linkCommand);
            if (linkResult.exitCode() != 0) {
                logger.logRunError("llvm-link", linkCommand, linkResult);
                return new TestResult.ExecutionError("llvm-link failed", linkCommand.toCommandLineString(),
                    linkResult.stderr());
            }
            logger.log("out.ll", tmpDir.resolve("out.ll"));
            var lliCommand = new ProcessExecutor.Command(CompilerTest.LLI_PATH, List.of("out.ll"), tmpDir,
                Optional.of(stdinContent));
            var lliResult = executor.execute(lliCommand);
            String actualOutput = lliResult.stdout();
            String stderr = lliResult.stderr();
            String expectedOutput = Files.readString(testCase.expectedOutputFile());
            logger.log("stdout.txt", actualOutput);
            if (!stderr.isBlank()) {
                logger.log("stderr.txt", stderr);
            }
            logger.log("expected.txt", expectedOutput);
            if (lliResult.exitCode() != 0) {
                logger.logRunError("lli", lliCommand, lliResult);
                return new TestResult.ExecutionError("lli execution failed", lliCommand.toCommandLineString(),
                    lliResult.stderr());
            }
            String normalizedActual = actualOutput.replaceAll("\\r\\n", "\n").trim();
            String normalizedExpected = expectedOutput.replaceAll("\\r\\n", "\n").trim();
            if (normalizedActual.equals(normalizedExpected)) {
                return new TestResult.Passed(actualOutput);
            } else {
                return new TestResult.Failed(actualOutput, expectedOutput, "Execution output mismatch");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try (Stream<Path> walk = Files.walk(tmpDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {

                    }
                });
            } catch (IOException e) {

            }
        }
    }

}
