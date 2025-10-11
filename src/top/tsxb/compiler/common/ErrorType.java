package top.tsxb.compiler.common;

/** The enum Error type. */
public enum ErrorType {
  INVALID_TOKEN("a", "Invalid Token");

  private final String code;
  private final String description;

  ErrorType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }
}
