package top.tsxb.compiler.frontend;

import java.util.List;

/** The type Token stream. */
public class TokenStream {
  private final List<Token> tokens;
  private int current = 0;

  /**
   * Instantiates a new Token stream.
   *
   * @param tokens the tokens
   */
  public TokenStream(List<Token> tokens) {
    this.tokens = tokens;
  }

  /**
   * Advance token.
   *
   * @return the token
   */
  public Token advance() {
    if (!isAtEnd()) {
      current++;
    }
    return previous();
  }

  public boolean isAtEnd() {
    return peek().type() == TokenType.EOF;
  }

  /**
   * Peek token.
   *
   * @return the token
   */
  public Token peek() {
    return tokens.get(current);
  }

  /**
   * Previous token.
   *
   * @return the token
   */
  public Token previous() {
    return tokens.get(current - 1);
  }
}
