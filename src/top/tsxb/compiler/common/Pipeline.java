package top.tsxb.compiler.common;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import top.tsxb.compiler.config.CompilerConfig;
import top.tsxb.compiler.frontend.cst.CstNode;
import top.tsxb.compiler.frontend.cst.TokenStream;
import top.tsxb.compiler.frontend.lexer.Lexer;
import top.tsxb.compiler.frontend.parser.Parser;
import top.tsxb.compiler.frontend.visitor.SyntaxPrinter;

/**
 * The type Pipeline.
 */
public class Pipeline {
    private ErrorReporter errorReporter;

    /**
     * Instantiates a new Pipeline.
     */
    public Pipeline() {
        this.errorReporter = new ErrorReporter();
    }

    /**
     * Run.
     */
    public void run() {
        try {
            String sourceCode = Files.readString(Paths.get(CompilerConfig.SOURCE_FILE));
            String output = run(sourceCode, CompilerConfig.CURRENT_HOMEWORK);

            if (errorReporter.hasErrors()) {
                writeErrors(output);
            } else {
                writeOutput(output);
            }
        } catch (Exception e) {
            System.err.println("Fatal Compiler Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    /**
     * Runs the compiler pipeline for a given stage on source code provided as a string. This method is designed for
     * testing and does not perform file I/O.
     *
     * @param sourceCode the source code
     * @param stage the stage ("lexer", "parser", etc.)
     * @return the string
     */
    public String run(String sourceCode, String stage) {
        this.errorReporter = new ErrorReporter();

        Lexer lexer = new Lexer(sourceCode, errorReporter);
        TokenStream tokenStream = lexer.scan();

        if ("lexer".equals(stage)) {
            if (errorReporter.hasErrors()) {
                return formatErrors(errorReporter.getErrors());
            } else {
                return tokenStream.getOutput();
            }
        }

        Parser parser = new Parser(tokenStream, errorReporter);
        CstNode compUnit = parser.parse();

        if ("parser".equals(stage)) {
            if (errorReporter.hasErrors()) {
                return formatErrors(errorReporter.getErrors());
            } else {
                return compUnit.accept(new SyntaxPrinter());
            }
        }

        throw new IllegalArgumentException("Unrecognized stage: " + stage);
    }

    private String formatErrors(List<ErrorEntry> errors) {
        return errors.stream().map(ErrorEntry::submission).collect(Collectors.joining(System.lineSeparator()));
    }

    private void writeOutput(String output) throws IOException {
        writeFile(CompilerConfig.OUTPUT_FILE, writer -> writer.print(output));
    }

    private void writeErrors(String output) throws IOException {
        writeFile(CompilerConfig.ERROR_FILE, writer -> writer.print(output));
    }

    private void writeFile(String filePath, Consumer<PrintWriter> writerAction) throws IOException {
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (PrintWriter writer = new PrintWriter(filePath)) {
            writerAction.accept(writer);
        }
    }

}
