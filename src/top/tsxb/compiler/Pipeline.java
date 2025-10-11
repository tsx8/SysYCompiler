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
import top.tsxb.compiler.config.Config;
import top.tsxb.compiler.frontend.Lexer;
import top.tsxb.compiler.frontend.Token;
import top.tsxb.compiler.frontend.TokenType;

/** The type top.tsxb.compiler.Pipeline. */
public class Pipeline {
  private final Config config;
  private final ErrorReporter errorReporter;

  /**
   * Instantiates a new top.tsxb.compiler.Pipeline.
   *
   * @param config the config
   */
  public Pipeline(Config config) {
    this.config = config;
    this.errorReporter = new ErrorReporter();
  }

  /** Run. */
  public void run() throws IOException {
    String sourceCode = Files.readString(Paths.get(config.getSourceFile()));

    Lexer lexer = new Lexer(sourceCode, errorReporter);
    List<Token> tokens = lexer.scan();

    if (errorReporter.hasErrors()) {
      writeErrors();
    } else {
      writeTokens(tokens);
    }
  }

  private void writeTokens(List<Token> tokens) throws IOException {
    writeFile(config.getOutputFile(), writer -> {
      for (Token token : tokens) {
        if (token.type() != TokenType.EOF) {
          writer.println(token);
        }
      }
    });
  }

  private void writeErrors() throws IOException {
    writeFile(config.getErrorFile(), writer -> {
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
