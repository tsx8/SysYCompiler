package top.tsxb.compiler.frontend.ast;

import java.util.List;

import top.tsxb.compiler.frontend.semantic.Type;

public class VarDecl extends Decl {
    public final boolean isConst;
    public final boolean isStatic;
    public final Type type;
    public final List<VarSpec> varSpecs;

    public final boolean isParamArray; // for FuncFParam
    public final String paramName;

    public VarDecl(boolean isConst, boolean isStatic, Type type, List<VarSpec> varSpecs) {
        this.isConst = isConst;
        this.isStatic = isStatic;
        this.type = type;
        this.varSpecs = varSpecs;
        this.isParamArray = false;
        this.paramName = null;
    }

    public VarDecl(Type type, String paramName, boolean isParamArray) {
        this.isConst = false;
        this.isStatic = false;
        this.type = type;
        this.varSpecs = null;
        this.isParamArray = isParamArray;
        this.paramName = paramName;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
