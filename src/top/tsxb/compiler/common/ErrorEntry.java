package top.tsxb.compiler.common;

/** The type Error entry. */
public record ErrorEntry(int lineNumber, String errorCode, String errorMsg)
    implements Comparable<ErrorEntry> {
  /**
   * Instantiates a new Error entry.
   *
   * @param lineNumber the line number
   * @param errorCode the error code
   * @param errorMsg the error msg
   */
  public ErrorEntry {}

  @Override
  public int compareTo(ErrorEntry o) {
    return Integer.compare(lineNumber, o.lineNumber);
  }

  /**
   * String in submission format.
   *
   * @return the string
   */
  public String submission() {
    return lineNumber + " " + errorCode;
  }

  @Override
  public String toString() {
    return "Error: (line " + lineNumber + "): [" + errorCode + "] " + errorMsg;
  }
}
