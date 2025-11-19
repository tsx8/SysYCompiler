package top.tsxb.compiler.frontend.semantic.ast;

public class ReturnStmt extends Stmt {
    public final Expr retVal;

    public ReturnStmt(Expr retVal) {
        this.retVal = retVal;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
