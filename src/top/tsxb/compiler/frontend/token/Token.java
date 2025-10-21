package top.tsxb.compiler.frontend.token;

/** The type Token.
 *
 * @param type The type of the token.
 * @param lexeme The raw string from the source code that the token represents.
 * @param line The line number where the token appears.
 *  */
public record Token(TokenType type, String lexeme, int line) {
  @Override
  public String toString() {
    return type + " " + lexeme;
  }
}
