package top.tsxb.compiler.ir.cst;

/**
 * The type Cst node.
 */
public abstract class CstNode {
    public abstract <T> T accept(CstVisitor<T> visitor);
}
