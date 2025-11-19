package top.tsxb.compiler.frontend.semantic.ast;

public class BreakStmt extends Stmt {
    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
