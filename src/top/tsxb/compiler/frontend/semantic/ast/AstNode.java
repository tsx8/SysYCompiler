package top.tsxb.compiler.frontend.semantic.ast;

import top.tsxb.compiler.frontend.semantic.sym.Symbol;

public abstract class AstNode {
    public int lineNumber;
    public Symbol symbol;

    public abstract <T> T accept(AstVisitor<T> visitor);
}
