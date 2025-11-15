package top.tsxb.compiler.frontend.ast;

import java.util.List;

import top.tsxb.compiler.frontend.semantic.Symbol;

public class LVal extends Expr {
    public final String name;
    public final List<Expr> indices;
    public Symbol symbol;

    public LVal(String name, List<Expr> indices) {
        this.name = name;
        this.indices = indices;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
