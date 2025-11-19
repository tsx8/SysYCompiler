package top.tsxb.compiler.frontend.semantic.ast;

import java.util.List;

import top.tsxb.compiler.frontend.semantic.sym.Symbol;

public class VarSpec extends AstNode {
    public final String name;
    public final List<Expr> dims;
    public final Expr initVal;

    public Symbol symbol;

    public VarSpec(String name, List<Expr> dims, Expr initVal) {
        this.name = name;
        this.dims = dims;
        this.initVal = initVal;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
