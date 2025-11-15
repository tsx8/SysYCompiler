package top.tsxb.compiler.frontend.ast;

import top.tsxb.compiler.frontend.semantic.Symbol;
import top.tsxb.compiler.frontend.semantic.Type;

public class FuncParam extends AstNode {
    public final Type type;
    public final String name;
    public final boolean isArray;

    public Symbol symbol;

    public FuncParam(Type type, String name, boolean isArray) {
        this.type = type;
        this.name = name;
        this.isArray = isArray;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
