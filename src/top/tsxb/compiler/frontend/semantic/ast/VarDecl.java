package top.tsxb.compiler.frontend.semantic.ast;

import top.tsxb.compiler.frontend.semantic.type.Type;

public final class VarDecl extends Decl {
    public final boolean isConst;
    public final boolean isStatic;
    public final Type type;
    public final String name;
    public final Expr dim;
    public final Expr initVal;

    public VarDecl(boolean isConst, boolean isStatic, Type type, String name, Expr dim, Expr initVal) {
        this.isConst = isConst;
        this.isStatic = isStatic;
        this.type = type;
        this.name = name;
        this.dim = dim;
        this.initVal = initVal;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
