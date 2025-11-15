package top.tsxb.compiler.ir.ast;

public class ExprStmt extends Stmt {
    public final Expr expr;

    public ExprStmt(Expr expr) {
        this.expr = expr;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
