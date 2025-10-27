package top.tsxb.compiler;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import top.tsxb.compiler.common.Pipeline;
import top.tsxb.compiler.config.CompilerConfig;
import top.tsxb.compiler.frontend.lexer.LexicalException;
import top.tsxb.compiler.frontend.parser.SyntacticException;

/**
 * A unified test harness for the SysY compiler. It runs test cases for the compiler stage defined
 * in {@link CompilerConfig}.
 *
 * <p>This test harness creates a detailed, structured log for each test run, with each test case's
 * artifacts stored in a separate subdirectory.
 */
public class CompilerTest {

  private static final Pipeline compilerPipeline = new Pipeline();

  private static final Path TEST_CASES_ROOT =
      Paths.get("testcases", CompilerConfig.CURRENT_HOMEWORK);

  // Base directory for storing all test run logs.
  private static final Path RUNS_LOG_DIR = Paths.get("out", "logs");

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   */
  public static void main(String[] args) {
    System.out.println("========================================");
    System.out.printf("  Running SysY Compiler Tests for: %s%n", CompilerConfig.CURRENT_HOMEWORK);
    System.out.println("========================================");

    if (!Files.isDirectory(TEST_CASES_ROOT)) {
      System.err.println(
          "ERROR: Test cases directory not found: " + TEST_CASES_ROOT.toAbsolutePath());
      System.err.println("HINT: Check the value of 'CURRENT_HOMEWORK' in CompilerConfig.java.");
      System.exit(1);
    }

    // Create a unique directory for this specific test run.
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    Path runLogDir = RUNS_LOG_DIR.resolve(String.format("run_%s", timestamp));
    try {
      Files.createDirectories(runLogDir);
    } catch (IOException e) {
      System.err.println(
          "ERROR: Could not create run log directory: " + runLogDir.toAbsolutePath());
      e.printStackTrace(System.err);
      System.exit(1);
    }

    int passed = 0;
    int failed = 0;
    List<Path> testDirs = findTestDirectories();

    for (Path testDir : testDirs) {
      System.out.printf("--- Running test: %s ---\n", testDir.getFileName());
      boolean result = runTestCase(testDir, runLogDir);
      if (result) {
        System.out.println("result: \u001B[32m[PASSED]\u001B[0m");
        passed++;
      } else {
        System.out.println("result: \u001B[31m[FAILED]\u001B[0m");
        failed++;
      }
    }

    String summary =
        String.format(
            "Stage: %s | Total: %d, Passed: %d, Failed: %d",
            CompilerConfig.CURRENT_HOMEWORK, (passed + failed), passed, failed);

    System.out.println("\n========================================");
    System.out.println("  Test Summary");
    System.out.println("========================================");
    System.out.println(summary);
    System.out.println("\nDetailed logs available in: " + runLogDir.toAbsolutePath());

    if (failed > 0) {
      System.exit(1);
    }
  }

  private static List<Path> findTestDirectories() {
    try (Stream<Path> paths = Files.list(TEST_CASES_ROOT)) {
      return paths
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing(Path::getFileName))
          .toList();
    } catch (IOException e) {
      System.err.println("ERROR: Could not read test case directories from " + TEST_CASES_ROOT);
      e.printStackTrace(System.err);
      throw new UncheckedIOException(e);
    }
  }

  private static boolean runTestCase(Path testDir, Path runLogDir) {
    Path caseLogDir = runLogDir.resolve(testDir.getFileName());
    try {
      Files.createDirectories(caseLogDir);
    } catch (IOException e) {
      System.err.println("ERROR: Could not create log directory for " + testDir.getFileName());
      e.printStackTrace(System.err);
      return false;
    }

    Path inputFile = testDir.resolve("testfile.txt");
    Path expectedFile = testDir.resolve("ans.txt");

    if (!Files.exists(inputFile) || !Files.exists(expectedFile)) {
      writeLog(caseLogDir, "err.log", "SKIPPED: Missing testfile.txt or ans.txt");
      return false;
    }

    try {
      String sourceCode = Files.readString(inputFile);
      String expectedOutput = Files.readString(expectedFile);
      writeLog(caseLogDir, "ans.log", expectedOutput);

      String actualOutput = compilerPipeline.run(sourceCode, CompilerConfig.CURRENT_HOMEWORK);
      writeLog(caseLogDir, "usr.log", actualOutput);

      String normalizedExpected = expectedOutput.replaceAll("\\r\\n", "\n").trim();
      String normalizedActual = actualOutput.replaceAll("\\r\\n", "\n").trim();

      if (normalizedExpected.equals(normalizedActual)) {
        return true; // PASSED
      } else {
        String diffMessage = """
            FAILED: Output mismatch.
            See ans.log (expected) and usr.log (actual) for details.
            """;
        writeLog(caseLogDir, "err.log", diffMessage);
        return false;
      }
    } catch (IOException e) {
      writeLog(
          caseLogDir,
          "err.log",
          "FAILED: I/O Exception during test execution.\n" + getStackTraceAsString(e));
      throw new UncheckedIOException("Failed to read test files in " + testDir, e);
    } catch (LexicalException | SyntacticException e) {
      String errorMessage =
          String.format("FAILED: Unhandled %s.\n\n", e.getClass().getSimpleName());
      writeLog(caseLogDir, "err.log", errorMessage + getStackTraceAsString(e));
      return false;
    }
  }

  private static void writeLog(Path dir, String fileName, String content) {
    try {
      Files.writeString(dir.resolve(fileName), content);
    } catch (IOException e) {
      // Log writing errors are critical and should be visible on the console.
      System.err.printf(
          "%nERROR: Failed to write log file %s in %s%n", fileName, dir.toAbsolutePath());
      e.printStackTrace(System.err);
    }
  }

  private static String getStackTraceAsString(Throwable throwable) {
    StringWriter stringWriter = new StringWriter();
    try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
      throwable.printStackTrace(printWriter);
    }
    return stringWriter.toString();
  }
}
