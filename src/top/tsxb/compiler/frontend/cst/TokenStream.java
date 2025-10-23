package top.tsxb.compiler.frontend.cst;

import java.util.List;
import java.util.stream.Collectors;

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
   * Gets output.
   *
   * @return the output
   */
  public String getOutput() {
    return tokens.stream()
           .filter(t -> t.type() != TokenType.EOF)
           .map(Token::toString)
           .collect(Collectors.joining());
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
    return peek(-1);
  }

  public boolean isAtEnd() {
    return peek(0).type() == TokenType.EOF;
  }

  /**
   * Peek token at a give offset.
   *
   * @param offset the offset
   * @return the token
   */
  public Token peek(int offset) {
    if (current + offset >= tokens.size()) {
      return tokens.get(tokens.size() - 1); // EOF
    }
    return tokens.get(current + offset);
  }
}
