package top.tsxb.compiler.frontend.semantic;

public record PointerType(Type baseType) implements Type {
    @Override
    public String toString() {
        return baseType.toString() + "*";
    }
}
