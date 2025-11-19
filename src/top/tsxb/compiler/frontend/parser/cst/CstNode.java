package top.tsxb.compiler.frontend.parser.cst;

/**
 * The type Cst node.
 */
public abstract class CstNode {
    public abstract <T> T accept(CstVisitor<T> visitor);
}
