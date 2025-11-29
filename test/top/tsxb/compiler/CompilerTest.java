package top.tsxb.compiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import top.tsxb.compiler.driver.CompilerConfig;
import top.tsxb.compiler.driver.Pipeline;
import top.tsxb.compiler.model.TestCase;
import top.tsxb.compiler.model.TestResult;
import top.tsxb.compiler.runner.ProcessExecutor;
import top.tsxb.compiler.runner.TestLogger;
import top.tsxb.compiler.strategy.IrExecutionStrategy;
import top.tsxb.compiler.strategy.LegacyFileCompareStrategy;
import top.tsxb.compiler.strategy.TestStrategy;

public class CompilerTest {
    private static final Path TEST_CASES_ROOT = Paths.get("testcases", CompilerConfig.CURRENT_HOMEWORK);
    private static final Path LOGS_ROOT =
        Paths.get("out", "logs", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.printf("  Running SysY Compiler Tests for: %s%n", CompilerConfig.CURRENT_HOMEWORK);
        System.out.println("========================================");

        if (!Files.isDirectory(TEST_CASES_ROOT)) {
            System.err.println("ERROR: Test cases directory not found: " + TEST_CASES_ROOT.toAbsolutePath());
            System.exit(1);
        }

        try {
            prepareLogDirectory();
        } catch (IOException e) {
            System.err.println("FATAL: Could not prepare log directory: " + LOGS_ROOT.toAbsolutePath());
            e.printStackTrace(System.err);
            System.exit(1);
        }

        TestStrategy strategy = createStrategy();
        int passed = 0;
        int failed = 0;

        try {
            strategy.prepare();
            List<Path> testDirs = findTestDirectories();
            for (Path testDir : testDirs) {
                System.out.printf("--- Running test: %-15s ", testDir.getFileName());
                TestCase testCase = new TestCase(testDir.getFileName().toString(), testDir.resolve("testfile.txt"),
                    testDir.resolve("ans.txt"), testDir.resolve("in.txt"));
                Path caseLogDir = LOGS_ROOT.resolve(testCase.name());
                TestLogger logger = new TestLogger(caseLogDir);
                logInitialArtifacts(testCase, logger);
                TestResult result = strategy.execute(testCase, logger);
                logFinalResult(result, logger);
                if (result instanceof TestResult.Passed p) {
                    System.out.println("\u001B[32m[PASSED]\u001B[0m");
                    passed++;
                } else if (result instanceof TestResult.Failed f) {
                    System.out.println("\u001B[31m[FAILED]\u001B[0m - " + f.reason());
                    System.err.println("Expected:\n---\n" + f.expectedOutput().trim() + "\n---");
                    System.err.println("Actual:\n---\n" + f.actualOutput().trim() + "\n---");
                    failed++;
                } else if (result instanceof TestResult.ExecutionError e) {
                    System.out.println("\u001B[31m[ERROR]\u001B[0m - " + e.summary());
                    System.err.println("Command: " + e.command());
                    System.err.println("Stderr:\n---\n" + e.stderr().trim() + "\n---");
                    failed++;
                }
            }
        } catch (Exception e) {
            System.err.println("\nFATAL ERROR during test execution: " + e.getMessage());
            e.printStackTrace(System.err);
            failed++;
        } finally {
            try {
                strategy.cleanup();
            } catch (IOException e) {
                System.err.println("ERROR during test cleanup: " + e.getMessage());
            }
        }
        printSummary(passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void prepareLogDirectory() throws IOException {
        if (Files.exists(LOGS_ROOT)) {
            try (Stream<Path> walk = Files.walk(LOGS_ROOT)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        Files.createDirectories(LOGS_ROOT);
        System.out.println("Clean log directory created at: " + LOGS_ROOT.toAbsolutePath());
    }

    private static void logInitialArtifacts(TestCase testCase, TestLogger logger) {
        try {
            logger.log("source.sysy", Files.readString(testCase.sourceFile()));
            logger.log("expected.txt", Files.readString(testCase.expectedOutputFile()));
        } catch (IOException e) {
            System.err.println("Warning: Could not log initial artifacts for " + testCase.name());
        }
    }

    private static void logFinalResult(TestResult result, TestLogger logger) {
        if (result instanceof TestResult.Passed p) {
            logger.log("output.log", p.actualOutput());
        } else if (result instanceof TestResult.Failed f) {
            logger.log("output.log", f.actualOutput());
            String errorDetails = "Reason: " + f.reason() + "\n\n--- EXPECTED ---\n" + f.expectedOutput()
                + "\n\n--- ACTUAL ---\n" + f.actualOutput();
            logger.log("error.log", errorDetails);
        } else if (result instanceof TestResult.ExecutionError e) {
            String errorDetails =
                "Summary: " + e.summary() + "\nCommand: " + e.command() + "\n\n--- STDERR ---\n" + e.stderr();
            logger.log("error.log", errorDetails);
        }
    }

    private static TestStrategy createStrategy() {
        var pipeline = new Pipeline();
        var executor = new ProcessExecutor();

        return switch (CompilerConfig.CURRENT_HOMEWORK) {
            case "lexer", "parser", "semantic" ->
                new LegacyFileCompareStrategy(pipeline, CompilerConfig.CURRENT_HOMEWORK);
            case "llvm" -> new IrExecutionStrategy(pipeline, executor);
            default -> throw new IllegalStateException(
                "No test strategy available for stage: " + CompilerConfig.CURRENT_HOMEWORK);
        };
    }

    private static List<Path> findTestDirectories() {
        try (Stream<Path> paths = Files.list(TEST_CASES_ROOT)) {
            return paths.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName)).toList();
        } catch (IOException e) {
            System.err.println("ERROR: Could not read test case directories from " + TEST_CASES_ROOT);
            e.printStackTrace(System.err);
            throw new UncheckedIOException(e);
        }
    }

    private static void printSummary(int passed, int failed) {
        System.out.println("\n=========================================");
        System.out.println("  Test Summary");
        System.out.println("=========================================");
        String summary = String.format("Stage: %s | Total: %d, Passed: %d, Failed: %d", CompilerConfig.CURRENT_HOMEWORK,
            (passed + failed), passed, failed);
        System.out.println(summary);
    }
}
