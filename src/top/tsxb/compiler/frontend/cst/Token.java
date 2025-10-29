package top.tsxb.compiler.frontend.cst;

/**
 * The type Token.
 */
public class Token extends CstNode {
    private final TokenType type;
    private final String lexeme;
    private final int line;

    /**
     * Instantiates a new Token.
     *
     * @param type the type
     * @param lexeme the lexeme
     * @param line the line
     */
    public Token(TokenType type, String lexeme, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
    }

    /**
     * Type token type.
     *
     * @return the token type
     */
    public TokenType type() {
        return type;
    }

    /**
     * Line int.
     *
     * @return the int
     */
    public int line() {
        return line;
    }

    public String lexeme() {
        return lexeme;
    }

    @Override
    public <T> T accept(CstVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return type.name() + " " + lexeme;
    }
}
