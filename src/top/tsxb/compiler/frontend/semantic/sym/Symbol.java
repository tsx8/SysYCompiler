package top.tsxb.compiler.frontend.semantic.sym;

import java.util.Collections;
import java.util.List;

import top.tsxb.compiler.frontend.semantic.type.ArrayType;
import top.tsxb.compiler.frontend.semantic.type.FunctionType;
import top.tsxb.compiler.frontend.semantic.type.IntegerType;
import top.tsxb.compiler.frontend.semantic.type.Type;

public record Symbol(String name, Type type, int scopeLevel, boolean isConst, boolean isStatic,
    List<Integer> initialValues) {
    public Symbol {
        initialValues = initialValues != null ? List.copyOf(initialValues) : Collections.emptyList();
    }

    private String typeInfo() {
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
        return String.format("%d %s %s", scopeLevel, name, typeInfo());
    }
}
