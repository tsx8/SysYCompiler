package top.tsxb.compiler.frontend.semantic.ast;

import top.tsxb.compiler.frontend.parser.cst.TokenType;

public class BinaryExpr extends Expr {
    public final Expr left;
    public final TokenType op;
    public final Expr right;

    public BinaryExpr(Expr left, TokenType op, Expr right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
