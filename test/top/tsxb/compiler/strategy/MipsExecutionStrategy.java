package top.tsxb.compiler.strategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

public record MipsExecutionStrategy(ProcessExecutor executor) implements TestStrategy {

    @Override
    public TestResult execute(TestCase testCase, TestLogger logger) {
        Pipeline pipeline = new Pipeline();
        Path tmpDir;
        try {
            tmpDir = Files.createTempDirectory("sysy_mips_test_" + testCase.name());
        } catch (IOException e) {
            return new TestResult.ExecutionError("Failed to create temp directory", "", e.getMessage());
        }
        try {
            String stdinContent = Files.exists(testCase.inputFile()) ? Files.readString(testCase.inputFile()) : "";
            logger.log("input.txt", stdinContent);
            String sourceCode = Files.readString(testCase.sourceFile());
            logger.log("testfile.txt", sourceCode);

            // Run pipeline to generate MIPS
            String mipsCode = pipeline.run(sourceCode, "mips");
            logger.log("generated.asm", mipsCode);

            Path mipsFile = tmpDir.resolve("test.asm");
            Files.writeString(mipsFile, mipsCode);

            // Run MARS
            // java -jar assets/mars.jar nc test.asm
            var marsCommand = new ProcessExecutor.Command("java", List.of("-jar",
                Paths.get(CompilerTest.MARS_PATH).toAbsolutePath().toString(), "nc", mipsFile.toString()), tmpDir,
                Optional.of(stdinContent));

            var marsResult = executor.execute(marsCommand);

            String actualOutput = marsResult.stdout();
            String stderr = marsResult.stderr();
            String expectedOutput = Files.readString(testCase.expectedOutputFile());

            logger.log("stdout.txt", actualOutput);
            if (!stderr.isBlank()) {
                logger.log("stderr.txt", stderr);
            }
            logger.log("expected.txt", expectedOutput);

            // MARS might exit with non-zero if there's a runtime error, but we should check stdout/stderr
            if (marsResult.stderr().contains("Error")) {
                logger.logRunError("mars", marsCommand, marsResult);
                return new TestResult.ExecutionError("MARS execution error", marsCommand.toCommandLineString(),
                    marsResult.stderr());
            }

            String normalizedActual = actualOutput.replaceAll("\\r\\n", "\n").trim();
            String normalizedExpected = expectedOutput.replaceAll("\\r\\n", "\n").trim();

            Path statsFile = tmpDir.resolve("InstructionStatistics.txt");
            if (Files.exists(statsFile)) {
                logger.log("InstructionStatistics.txt", statsFile);
            }

            if (normalizedActual.equals(normalizedExpected)) {
                return new TestResult.Passed(actualOutput);
            } else {
                return new TestResult.Failed(actualOutput, expectedOutput, "MIPS execution output mismatch");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try (Stream<Path> walk = Files.walk(tmpDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
