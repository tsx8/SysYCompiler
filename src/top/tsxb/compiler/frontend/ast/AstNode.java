package top.tsxb.compiler.frontend.ast;

public abstract class AstNode {
    public int lineNumber;

    public abstract <T> T accept(AstVisitor<T> visitor);
}
