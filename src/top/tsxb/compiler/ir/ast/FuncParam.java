package top.tsxb.compiler.ir.ast;

import top.tsxb.compiler.ir.symtab.Symbol;
import top.tsxb.compiler.ir.type.Type;

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
