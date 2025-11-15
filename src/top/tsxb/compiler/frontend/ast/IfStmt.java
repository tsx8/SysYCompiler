package top.tsxb.compiler.frontend.ast;

public class IfStmt extends Stmt {
    public final Expr cond;
    public final Stmt then;
    public final Stmt elseStmt;

    public IfStmt(Expr cond, Stmt then, Stmt elseStmt) {
        this.cond = cond;
        this.then = then;
        this.elseStmt = elseStmt;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
