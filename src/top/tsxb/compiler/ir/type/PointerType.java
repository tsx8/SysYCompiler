package top.tsxb.compiler.ir.type;

public record PointerType(Type baseType) implements Type {
    @Override
    public String toString() {
        return baseType.toString() + "*";
    }
}
