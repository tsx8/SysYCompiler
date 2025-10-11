package top.tsxb.compiler.common;

import java.util.ArrayList;
import java.util.List;

/** The type Error reporter. */
public class ErrorReporter {
  private final List<ErrorEntry> errors;

  /** Instantiates a new Error reporter. */
  public ErrorReporter() {
    errors = new ArrayList<>();
  }

  /**
   * Report.
   *
   * @param lineNumber the line number
   * @param type the type
   * @param args the args
   */
  public void report(int lineNumber, ErrorType type, Object... args) {
    String message = String.format(type.getDescription(), args);
    errors.add(new ErrorEntry(lineNumber, type.getCode(), message));
  }

  /**
   * Has errors boolean.
   *
   * @return the boolean
   */
  public boolean hasErrors() {
    return !errors.isEmpty();
  }



}
