package top.tsxb.compiler.frontend.ast;

public class AssignStmt extends Stmt {
    public final LVal lVal;
    public final Expr rVal;

    public AssignStmt(LVal lVal, Expr rVal) {
        this.lVal = lVal;
        this.rVal = rVal;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
