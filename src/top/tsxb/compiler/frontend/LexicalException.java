package top.tsxb.compiler.frontend;

/** An unchecked exception thrown when a fatal, unrecoverable error occurs during lexical
 * analysis. */
public class LexicalException extends RuntimeException {
  /**
   * Instantiates a new Lexical exception.
   *
   * @param message the message
   */
  public LexicalException(String message) {
    super((message));
  }
}
