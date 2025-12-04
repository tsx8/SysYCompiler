package top.tsxb.compiler.frontend.semantic.type;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record FunctionType(Type returnType, List<Type> paramTypes) implements Type {
    public FunctionType {
        Objects.requireNonNull(returnType);
        Objects.requireNonNull(paramTypes);
    }

    @Override
    public boolean isSame(Type other) {
        if (!(other instanceof FunctionType ft)) {
            return false;
        }
        if (!this.returnType.isSame(ft.returnType)) {
            return false;
        }
        if (this.paramTypes.size() != ft.paramTypes.size()) {
            return false;
        }
        for (int i = 0; i < this.paramTypes.size(); i++) {
            if (!this.paramTypes.get(i).isSame(ft.paramTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCastableTo(Type other) {
        return isSame(other);
    }

    @Override
    public String toString() {
        String params = paramTypes.stream().map(Type::toString).collect(Collectors.joining(", "));
        return String.format("%s (%s)", returnType, params);
    }
}
