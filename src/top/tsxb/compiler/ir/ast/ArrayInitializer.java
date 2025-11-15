package top.tsxb.compiler.ir.ast;

import java.util.List;

public class ArrayInitializer extends Expr {
    public final List<Expr> values;

    public ArrayInitializer(List<Expr> values) {
        this.values = values;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
