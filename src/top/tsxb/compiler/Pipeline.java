package top.tsxb.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.config.Config;

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

  }
}
