package top.tsxb.compiler.config;

/** The type Compiler config. */
public final class CompilerConfig {
  private CompilerConfig() {}

  public static final String CURRENT_HOMEWORK = "Lexer";

  public static final String SOURCE_FILE = "testfile.txt";

  public static final String ERROR_FILE = "error.txt";

  public static final String OUTPUT_FILE = determineOutputFile();

  private static String determineOutputFile() {
    return switch (CURRENT_HOMEWORK) {
      case "Lexer" -> "lexer.txt";
      case "Parser" -> "parser.txt";
      default -> "output.txt";
    };
  }
}
