package top.tsxb.compiler.frontend.semantic.ast;

import top.tsxb.compiler.frontend.parser.cst.TokenType;

public class UnaryExpr extends Expr {
    public final TokenType op;
    public final Expr operand;

    public UnaryExpr(TokenType op, Expr operand) {
        this.op = op;
        this.operand = operand;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
