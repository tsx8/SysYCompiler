package top.tsxb.compiler.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/** The type Config. */
public class Config {
  private final Properties props = new Properties();

  /**
   * Instantiates a new Config.
   *
   * @param fileName the file name
   */
  public Config(String fileName) {
    try (InputStream input = new FileInputStream(fileName)) {
      props.load(input);
    } catch (Exception e) {
      try (InputStream input = getClass().getClassLoader().getResourceAsStream(fileName)) {
        if (input == null) {
          System.err.println("Cannot find configuration file: " + fileName);
          return;
        }
        props.load(input);
      } catch (Exception e2) {
        e2.printStackTrace(System.err);
      }
    }
  }

  public String getSourceFile() {
    return props.getProperty("source.file", "testfile.txt");
  }

  public String getCurrentHomework() {
    return props.getProperty("current.homework", "Lexer");
  }

  /**
   * Gets output file.
   *
   * @return the output file
   */
  public String getOutputFile() {
    String defaultOutput = switch (getCurrentHomework()) {
      case "Lexer" -> "lexer.txt";
      case "Parser" -> "parser.txt";
      default -> "output.txt";
    };
    return props.getProperty("output.file", defaultOutput);
  }

  public String getErrorFile() {
    return props.getProperty("error.file", "error.txt");
  }
}
