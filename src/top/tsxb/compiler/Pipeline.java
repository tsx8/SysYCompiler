package top.tsxb.compiler;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import top.tsxb.compiler.common.ErrorEntry;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.config.CompilerConfig;
import top.tsxb.compiler.frontend.lexer.Lexer;
import top.tsxb.compiler.frontend.lexer.LexicalException;
import top.tsxb.compiler.frontend.lexer.Token;
import top.tsxb.compiler.frontend.lexer.TokenStream;
import top.tsxb.compiler.frontend.lexer.TokenType;
import top.tsxb.compiler.frontend.parser.Parser;
import top.tsxb.compiler.frontend.parser.SyntacticException;
import top.tsxb.compiler.frontend.parser.SyntaxWriter;

/** The type top.tsxb.compiler.Pipeline. */
public class Pipeline {
  private final ErrorReporter errorReporter;

  /**
   * Instantiates a new top.tsxb.compiler.Pipeline.
   */
  public Pipeline() {
    this.errorReporter = new ErrorReporter();
  }

  /** Run. */
  public void run() throws IOException {
    try {
      String sourceCode = Files.readString(Paths.get(CompilerConfig.SOURCE_FILE));

      Lexer lexer = new Lexer(sourceCode, errorReporter);
      List<Token> tokens = lexer.scan();

      if (errorReporter.hasErrors()) {
        writeErrors();
      }
      // else {
      //   writeLexerOutput(tokens);
      // }

      TokenStream tokenStream = new TokenStream(tokens);
      SyntaxWriter syntaxWriter = new SyntaxWriter(!errorReporter.hasErrors());
      Parser parser = new Parser(tokenStream, errorReporter, syntaxWriter);
      parser.parse();

      if (errorReporter.hasErrors()) {
        writeErrors();
      } else {
        writeParserOutput(syntaxWriter.getOutput());
      }
    } catch (LexicalException e) {
      System.err.println("Fatal Lexical Error: " + e.getMessage());
      e.printStackTrace(System.err);
    } catch (SyntacticException e) {
      System.err.println("Fatal Parser Error: " + e.getMessage());
      e.printStackTrace(System.err);
    }
  }

  private void writeLexerOutput(List<Token> tokens) throws IOException {
    writeFile(CompilerConfig.OUTPUT_FILE, writer -> {
      for (Token token : tokens) {
        if (token.type() != TokenType.EOF) {
          writer.println(token);
        }
      }
    });
  }

  private void writeParserOutput(String output) throws IOException {
    writeFile(CompilerConfig.OUTPUT_FILE, writer -> writer.print(output));
  }

  private void writeErrors() throws IOException {
    writeFile(CompilerConfig.ERROR_FILE, writer -> {
      List<ErrorEntry> sortedErrors = errorReporter.getErrors();
      for (ErrorEntry entry : sortedErrors) {
        writer.println(entry.submission());
      }
    });
  }

  private void writeFile(String filePath, Consumer<PrintWriter> writerAction) throws  IOException {
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
