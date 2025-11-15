package top.tsxb.compiler.frontend.ast;

public class ContinueStmt extends Stmt {
    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
