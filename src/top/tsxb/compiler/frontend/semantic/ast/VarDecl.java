package top.tsxb.compiler.frontend.semantic.ast;

import java.util.List;

import top.tsxb.compiler.frontend.semantic.type.Type;

public class VarDecl extends Decl {
    public final boolean isConst;
    public final boolean isStatic;
    public final Type type;
    public final List<VarSpec> varSpecs;

    public VarDecl(boolean isConst, boolean isStatic, Type type, List<VarSpec> varSpecs) {
        this.isConst = isConst;
        this.isStatic = isStatic;
        this.type = type;
        this.varSpecs = varSpecs;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
