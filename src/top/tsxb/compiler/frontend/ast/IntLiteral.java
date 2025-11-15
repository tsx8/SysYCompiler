package top.tsxb.compiler.frontend.ast;

public class IntLiteral extends Expr {
    public final int value;

    public IntLiteral(int value) {
        this.value = value;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
