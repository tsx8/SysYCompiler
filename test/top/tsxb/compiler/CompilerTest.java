package top.tsxb.compiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import top.tsxb.compiler.driver.CompilerConfig;
import top.tsxb.compiler.model.TestCase;
import top.tsxb.compiler.model.TestResult;
import top.tsxb.compiler.runner.ProcessExecutor;
import top.tsxb.compiler.runner.TestLogger;
import top.tsxb.compiler.strategy.IrExecutionStrategy;
import top.tsxb.compiler.strategy.LegacyFileCompareStrategy;
import top.tsxb.compiler.strategy.MipsExecutionStrategy;
import top.tsxb.compiler.strategy.TestStrategy;

public class CompilerTest {
    public static final String CLANG_PATH = "clang";
    public static final String LLVM_LINK_PATH = "llvm-link";
    public static final String LLI_PATH = "lli";
    public static final String MARS_PATH = "assets/mars.jar";
    public static final Path LIBSYSY_DIR = Paths.get("assets/libsysy");
    public static final int TEST_THREADS = Runtime.getRuntime().availableProcessors();

    private static final Path TEST_CASES_ROOT = Paths.get("testcases", CompilerConfig.CURRENT_HOMEWORK);
    private static final Path LOGS_ROOT =
        Paths.get("out", "logs", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
    private static final Path PREVIOUS_LOGS_ROOT = findPreviousLogDirectory();
    private static final Map<String, Double> currentFinalCycles = new ConcurrentHashMap<>();
    private static final Map<String, Double> previousFinalCycles = loadPreviousFinalCycles();

    private static Path findPreviousLogDirectory() {
        Path logsParent = Paths.get("out", "logs");
        if (!Files.exists(logsParent)) return null;
        try (Stream<Path> paths = Files.list(logsParent)) {
            return paths.filter(Files::isDirectory)
                .filter(p -> !p.getFileName().toString().equals(LOGS_ROOT.getFileName().toString()))
                .max(Comparator.comparing(p -> p.getFileName().toString()))
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, Double> loadPreviousFinalCycles() {
        Map<String, Double> results = new HashMap<>();
        if (PREVIOUS_LOGS_ROOT == null) return results;
        try (Stream<Path> paths = Files.list(PREVIOUS_LOGS_ROOT)) {
            paths.filter(Files::isDirectory).forEach(caseDir -> {
                Double cycle = extractFinalCycle(caseDir);
                if (cycle != null) {
                    results.put(caseDir.getFileName().toString(), cycle);
                }
            });
        } catch (IOException ignored) {}
        return results;
    }

    private static Double extractFinalCycle(Path caseLogDir) {
        Path statsFile = caseLogDir.resolve("InstructionStatistics.txt");
        if (!Files.exists(statsFile)) return null;
        try {
            List<String> lines = Files.readAllLines(statsFile);
            for (String line : lines) {
                if (line.startsWith("Final Cycle:")) {
                    return Double.parseDouble(line.substring("Final Cycle:".length()).trim());
                }
            }
        } catch (IOException | NumberFormatException ignored) {}
        return null;
    }

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

        try {
            strategy.prepare();
        } catch (Exception e) {
            System.err.println("\nFATAL: Strategy preparation failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }

        LongAdder passed = new LongAdder();
        LongAdder failed = new LongAdder();

        List<Path> testDirs = findTestDirectories(args);
        if (testDirs.size() == 1) {
            System.out.printf("Running single test case: %s%n", testDirs.get(0).getFileName());
        } else if (args.length > 0) {
            System.out.printf("Running %d selected test cases with %d threads...%n", testDirs.size(), TEST_THREADS);
        } else {
            System.out.printf("Starting parallel execution with %d threads...%n", TEST_THREADS);
        }

        ForkJoinPool customThreadPool = new ForkJoinPool(TEST_THREADS);
        try {
            customThreadPool.submit(() -> testDirs.parallelStream().forEach(testDir -> {
                String testName = testDir.getFileName().toString();
                try {
                    TestCase testCase = new TestCase(testName, testDir.resolve("testfile.txt"), testDir.resolve("ans.txt"),
                        testDir.resolve("in.txt"));
                    Path caseLogDir = LOGS_ROOT.resolve(testCase.name());
                    TestLogger logger = new TestLogger(caseLogDir);

                    TestResult result = strategy.execute(testCase, logger);
                    Double cycle = extractFinalCycle(caseLogDir);
                    if (cycle != null) {
                        currentFinalCycles.put(testName, cycle);
                    }

                    synchronized (System.out) {
                        String cycleInfo = "";
                        if (cycle != null) {
                            cycleInfo = String.format(" | FinalCycle: %.1f", cycle);
                            Double prevCycle = previousFinalCycles.get(testName);
                            if (prevCycle != null && prevCycle > 0 && !prevCycle.equals(cycle)) {
                                double diffPercent = (cycle - prevCycle) / prevCycle * 100.0;
                                cycleInfo += String.format(" (%s%.2f%%)", diffPercent > 0 ? "+" : "", diffPercent);
                            }
                        }
                        if (result instanceof TestResult.Passed) {
                            System.out.printf("--- [%-15s] \u001B[32m[PASSED]\u001B[0m%s%n", testName, cycleInfo);
                            passed.increment();
                        } else if (result instanceof TestResult.Failed f) {
                            System.out.printf("--- [%-15s] \u001B[31m[FAILED]\u001B[0m%s - %s%n", testName, cycleInfo, f.reason());
                            failed.increment();
                        } else if (result instanceof TestResult.ExecutionError e) {
                            System.out.printf("--- [%-15s] \u001B[31m[ERROR]\u001B[0m%s - %s%n", testName, cycleInfo, e.summary());
                            failed.increment();
                        }
                    }
                } catch (Exception e) {
                    synchronized (System.err) {
                        System.err.printf("--- [%-15s] \u001B[31m[CRASH]\u001B[0m%n", testName);
                        e.printStackTrace(System.err);
                    }
                    failed.increment();
                }
            })).get();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("FATAL: Parallel execution interrupted or failed: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            customThreadPool.shutdown();
        }

        strategy.cleanup();

        printSummary(passed.intValue(), failed.intValue());
        if (failed.intValue() > 0) {
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

    private static TestStrategy createStrategy() {
        var executor = new ProcessExecutor();

        return switch (CompilerConfig.CURRENT_HOMEWORK) {
            case "lexer", "parser", "semantic" ->
                new LegacyFileCompareStrategy(CompilerConfig.CURRENT_HOMEWORK);
            case "llvm" -> new IrExecutionStrategy(executor);
            case "mips" -> new MipsExecutionStrategy(executor);
            default -> throw new IllegalStateException(
                "No test strategy available for stage: " + CompilerConfig.CURRENT_HOMEWORK);
        };
    }

    private static List<Path> findTestDirectories(String[] args) {
        try (Stream<Path> paths = Files.list(TEST_CASES_ROOT)) {
            List<Path> allDirs =
                paths.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName)).toList();
            if (args.length > 0) {
                Set<String> targets = new HashSet<>(Arrays.asList(args));
                List<Path> filtered =
                    allDirs.stream().filter(p -> targets.contains(p.getFileName().toString())).toList();
                if (filtered.isEmpty()) {
                    System.err.printf("ERROR: No matching test cases found for %s in %s%n", targets, TEST_CASES_ROOT);
                    System.exit(1);
                }
                return filtered;
            }
            return allDirs;
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

        if (!currentFinalCycles.isEmpty()) {
            double totalCurrent = currentFinalCycles.values().stream().mapToDouble(Double::doubleValue).sum();
            System.out.printf("Total FinalCycle: %.1f", totalCurrent);

            double totalPreviousCommon = 0;
            double totalCurrentCommon = 0;
            int commonCount = 0;

            for (Map.Entry<String, Double> entry : currentFinalCycles.entrySet()) {
                String name = entry.getKey();
                if (previousFinalCycles.containsKey(name)) {
                    totalCurrentCommon += entry.getValue();
                    totalPreviousCommon += previousFinalCycles.get(name);
                    commonCount++;
                }
            }

            if (commonCount > 0 && totalPreviousCommon > 0) {
                double diffPercent = (totalCurrentCommon - totalPreviousCommon) / totalPreviousCommon * 100.0;
                if (diffPercent != 0) {
                    System.out.printf(" (%s%.2f%% vs previous %d common cases)", diffPercent > 0 ? "+" : "", diffPercent, commonCount);
                }
            }
            System.out.println();

            String maxIncName = null;
            double maxIncPercent = 0;
            String maxDecName = null;
            double maxDecPercent = 0;

            for (Map.Entry<String, Double> entry : currentFinalCycles.entrySet()) {
                String name = entry.getKey();
                Double current = entry.getValue();
                Double prev = previousFinalCycles.get(name);
                if (prev != null && prev > 0) {
                    double diffPercent = (current - prev) / prev * 100.0;
                    if (diffPercent > maxIncPercent) {
                        maxIncPercent = diffPercent;
                        maxIncName = name;
                    }
                    if (diffPercent < maxDecPercent) {
                        maxDecPercent = diffPercent;
                        maxDecName = name;
                    }
                }
            }

            if (maxIncName != null) {
                System.out.printf("Max Increase: %s (+%.2f%%)%n", maxIncName, maxIncPercent);
            }
            if (maxDecName != null) {
                System.out.printf("Max Decrease: %s (%.2f%%)%n", maxDecName, maxDecPercent);
            }
        }
    }
}
