package top.tsxb.compiler.frontend;

import top.tsxb.compiler.common.ErrorEntry;
import top.tsxb.compiler.common.ErrorReporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LexerTest {
  private static final Path TEST_CASES_ROOT = Paths.get("testcases", "lexer");

  public static void main(String[] args) {
    System.out.println("========================================");
    System.out.println("  Running SysY Lexer Tests");
    System.out.println("========================================");

    if (!Files.isDirectory(TEST_CASES_ROOT)) {
      System.err.println("ERROR: Test cases directory not found: " + TEST_CASES_ROOT.toAbsolutePath());
      System.exit(1);
    }

    int passed = 0;
    int failed = 0;
    List<Path> testDirs;

    try (Stream<Path> paths = Files.list(TEST_CASES_ROOT)) {
      testDirs = paths.filter(Files::isDirectory)
                 .sorted(Comparator.comparing(Path::getFileName))
                 .toList();
    } catch (IOException e) {
      System.err.println("ERROR: Could not read test case directories from " + TEST_CASES_ROOT);
      e.printStackTrace(System.err);
      System.exit(1);
      return;
    }

    for (Path testDir : testDirs) {
      System.out.println("\n--- Running test: " + testDir.getFileName() + " ---");
      boolean result = runTestCase(testDir);
      if (result) {
        passed++;
      } else {
        failed++;
      }
    }

    System.out.println("\n========================================");
    System.out.println("  Test Summary");
    System.out.println("========================================");
    System.out.printf("Total: %d, Passed: %d, Failed: %d\n", (passed + failed), passed, failed);

    if (failed > 0) {
      System.exit(1);
    }
  }

  private static boolean runTestCase(Path testDir) {
    Path inputFile = testDir.resolve("testfile.txt");
    Path expectedFile = testDir.resolve("ans.txt");

    if (!Files.exists(inputFile) || !Files.exists(expectedFile)) {
      System.out.println("STATUS: \u001B[33mSKIPPED\u001B[0m (Missing testfile.txt or ans.txt)");
      return false;
    }

    try {
      String sourceCode = Files.readString(inputFile);
      String expectedOutput = Files.readString(expectedFile);

      ErrorReporter errorReporter = new ErrorReporter();
      Lexer lexer = new Lexer(sourceCode, errorReporter);
      List<Token> tokens = lexer.scan();

      String actualOutput;
      if (errorReporter.hasErrors()) {
        actualOutput = errorReporter.getErrors().stream()
                       .map(ErrorEntry::submission)
                       .collect(Collectors.joining(System.lineSeparator()));
      } else {
        actualOutput = tokens.stream()
                       .filter(t -> t.type() != TokenType.EOF)
                       .map(Token::toString)
                       .collect(Collectors.joining(System.lineSeparator()));
      }

      String normalizedExpected = expectedOutput.replaceAll("\\r\\n", "\n").trim();
      String normalizedActual = actualOutput.replaceAll("\\r\\n", "\n").trim();

      if (normalizedExpected.equals(normalizedActual)) {
        System.out.println("STATUS: \u001B[32mPASSED\u001B[0m");
        return true;
      } else {
        System.out.println("STATUS: \u001B[31mFAILED\u001B[0m");
        System.out.println("\n---------- EXPECTED ----------");
        System.out.println(expectedOutput);
        System.out.println("------------------------------\n");
        System.out.println("----------- ACTUAL -----------");
        System.out.println(actualOutput);
        System.out.println("------------------------------\n");
        return false;
      }

    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read test files in " + testDir, e);
    } catch (LexicalException e) {
      System.out.println("STATUS: \u001B[31mFAILED\u001B[0m with unhandled LexicalException");
      e.printStackTrace(System.err);
      return false;
    }
  }
}
