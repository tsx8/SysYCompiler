package top.tsxb.compiler.driver;

/**
 * The type Compiler config.
 */
public final class CompilerConfig {
    public static final boolean DEBUG = false;
    public static final String CURRENT_HOMEWORK = "semantic";
    public static final String SOURCE_FILE = "testfile.txt";
    public static final String ERROR_FILE = "error.txt";
    public static final String OUTPUT_FILE = determineOutputFile();

    private CompilerConfig() {}

    private static String determineOutputFile() {
        return switch (CURRENT_HOMEWORK) {
            case "lexer" -> "lexer.txt";
            case "parser" -> "parser.txt";
            case "semantic" -> "symbol.txt";
            default -> "output.txt";
        };
    }
}
