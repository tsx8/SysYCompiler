package top.tsxb.compiler.frontend.semantic;

import java.util.Collections;
import java.util.List;

public record Symbol(String name, Type type, int scopeLevel, boolean isConst, boolean isStatic, List<Integer> dims,
    Integer constValue) {
    public Symbol(String name, Type type, int scopeLevel, boolean isConst, boolean isStatic, List<Integer> dims,
        Integer constValue) {
        this.name = name;
        this.type = type;
        this.scopeLevel = scopeLevel;
        this.isConst = isConst;
        this.isStatic = isStatic;
        this.dims = dims != null ? List.copyOf(dims) : Collections.emptyList();
        this.constValue = constValue;
    }

    private String getSymbolTypeForOutput() {
        if (type instanceof IntegerType) {
            if (isConst)
                return "ConstInt";
            if (isStatic)
                return "StaticInt";
            return "Int";
        }
        if (type instanceof ArrayType) {
            if (isConst)
                return "ConstIntArray";
            if (isStatic)
                return "StaticIntArray";
            return "IntArray";
        }
        if (type instanceof FunctionType funcType) {
            if (funcType.returnType() instanceof IntegerType) {
                return "IntFunc";
            } else {
                return "VoidFunc";
            }
        }
        return "Unknown"; // Should not happen
    }

    @Override
    public String toString() {
        return String.format("%d %s %s", scopeLevel, name, getSymbolTypeForOutput());
    }
}
