package top.tsxb.compiler.driver;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The type Compiler config.
 */
public final class CompilerConfig {
    public static final boolean DEBUG = false;
    public static final String PROGRAMMING_LANGUAGE = "java";
    public static final String OBJECT_CODE = "llvm"; // "mips", "llvm"
    public static final String CURRENT_HOMEWORK = "llvm";
    public static final String SOURCE_FILE = "testfile.txt";
    public static final String ERROR_FILE = "error.txt";
    public static final String OUTPUT_FILE = determineOutputFile();
    public static final String CLANG_PATH = "clang";
    public static final String LLVM_LINK_PATH = "llvm-link";
    public static final String LLI_PATH = "lli";
    public static final Path LIBSYSY_DIR = Paths.get("assets/libsysy");

    private CompilerConfig() {}

    private static String determineOutputFile() {
        return switch (CURRENT_HOMEWORK) {
            case "lexer" -> "lexer.txt";
            case "parser" -> "parser.txt";
            case "semantic" -> "symbol.txt";
            case "llvm" -> "llvm_ir.txt";
            default -> "output.txt";
        };
    }
}
