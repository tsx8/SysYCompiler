package top.tsxb.compiler.frontend.parser;

/** An unchecked exception thrown when a fatal, unrecoverable error occurs during
 * syntactic analysis. */
public class SyntacticException extends RuntimeException {
  /**
   * Instantiates a new Parser exception.
   *
   * @param message the message
   */
  public SyntacticException(String message) {
    super(message);
  }
}
