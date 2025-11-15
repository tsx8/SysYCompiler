package top.tsxb.compiler.frontend.ast;

import java.util.List;

import top.tsxb.compiler.frontend.semantic.Symbol;

public class FuncCall extends Expr {
    public final String name;
    public final List<Expr> args;
    public Symbol symbol;

    public FuncCall(String name, List<Expr> args) {
        this.name = name;
        this.args = args;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
