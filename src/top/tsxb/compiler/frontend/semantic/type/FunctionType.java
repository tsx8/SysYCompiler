package top.tsxb.compiler.frontend.semantic.type;

import java.util.List;
import java.util.stream.Collectors;

public record FunctionType(Type returnType, List<Type> paramTypes) implements Type {
    @Override
    public String toString() {
        String params = paramTypes.stream().map(Type::toString).collect(Collectors.joining(", "));
        return String.format("%s (%s)", returnType.toString(), params);
    }
}
