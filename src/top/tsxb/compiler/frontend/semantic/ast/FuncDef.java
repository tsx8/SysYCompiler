package top.tsxb.compiler.frontend.semantic.ast;

import java.util.List;

import top.tsxb.compiler.frontend.semantic.sym.Symbol;
import top.tsxb.compiler.frontend.semantic.type.Type;

public class FuncDef extends Decl {
    public final Type funcType;
    public final String name;
    public final List<FuncParam> params;
    public final BlockStmt body;

    public Symbol symbol;

    public FuncDef(Type funcType, String name, List<FuncParam> params, BlockStmt body) {
        this.funcType = funcType;
        this.name = name;
        this.params = params;
        this.body = body;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
