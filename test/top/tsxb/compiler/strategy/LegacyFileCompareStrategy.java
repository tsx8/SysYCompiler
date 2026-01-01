package top.tsxb.compiler.strategy;

import java.io.IOException;
import java.nio.file.Files;

import top.tsxb.compiler.driver.Pipeline;
import top.tsxb.compiler.model.TestCase;
import top.tsxb.compiler.model.TestResult;
import top.tsxb.compiler.runner.TestLogger;

public record LegacyFileCompareStrategy(Pipeline pipeline, String targetStage) implements TestStrategy {

    @Override
    public TestResult execute(TestCase testCase, TestLogger logger) {
        try {
            String sourceCode = Files.readString(testCase.sourceFile());
            logger.log("testfile.txt", sourceCode);
            String expectedOutput = Files.readString(testCase.expectedOutputFile());
            String actualOutput = pipeline.run(sourceCode, targetStage);
            logger.log("stdout.txt", actualOutput);
            logger.log("expected.txt", expectedOutput);
            String normalizedExpected = expectedOutput.replaceAll("\\r\\n", "\n").trim();
            String normalizedActual = actualOutput.replaceAll("\\r\\n", "\n").trim();
            if (normalizedExpected.equals(normalizedActual)) {
                return new TestResult.Passed(actualOutput);
            } else {
                return new TestResult.Failed(actualOutput, expectedOutput, "Output mismatch");
            }
        } catch (IOException e) {
            return new TestResult.ExecutionError("Failed to read test files", "File IO", e.getMessage());
        } catch (Exception e) {
            return new TestResult.ExecutionError("Unhandled compiler exception", "Compiler Pipeline",
                e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
