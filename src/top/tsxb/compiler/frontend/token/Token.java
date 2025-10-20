package top.tsxb.compiler.frontend.token;

/** The type Token.
 *
 * @param type The type of the token.
 * @param lexeme The raw string from the source code that the token represents.
 * @param value The value of the token, if applicable (e.g., an Integer for INTCON).
 * @param line The line number where the token appears.
 *  */
public record Token(TokenType type, String lexeme, Object value, int line) {
  @Override
  public String toString() {
    return type + " " + lexeme;
  }
}
