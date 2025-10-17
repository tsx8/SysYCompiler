package top.tsxb.compiler.frontend.parser;

import java.util.Set;
import top.tsxb.compiler.frontend.lexer.Token;

/** The type Syntax writer. */
public class SyntaxWriter {
  private final StringBuilder output = new StringBuilder();
  private final boolean enabled;

  private static final Set<String> OMITTED_NON_TERMINALS = Set.of(
      "BlockItem", "Decl", "BType"
  );

  /**
   * Instantiates a new Syntax writer.
   *
   * @param enabled the enabled
   */
  public SyntaxWriter(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Write token.
   *
   * @param token the token
   */
  public void writeToken(Token token) {
    if (enabled) {
      output.append(token.toString()).append(System.lineSeparator());
    }
  }

  /**
   * Write non terminal.
   *
   * @param name the name
   */
  public void writeNonTerminal(String name) {
    if (enabled && !OMITTED_NON_TERMINALS.contains(name)) {
      output.append("<").append(name).append(">").append(System.lineSeparator());
    }
  }

  public String getOutput() {
    return output.toString();
  }
}
