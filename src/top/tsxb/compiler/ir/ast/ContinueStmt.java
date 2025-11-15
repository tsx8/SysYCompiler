package top.tsxb.compiler.ir.ast;

public class ContinueStmt extends Stmt {
    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
