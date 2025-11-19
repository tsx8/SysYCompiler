package top.tsxb.compiler.frontend.semantic.type;

import java.util.Objects;

public record PointerType(Type baseType) implements Type {
    public PointerType {
        Objects.requireNonNull(baseType);
    }

    @Override
    public boolean isSame(Type other) {
        if (other instanceof PointerType ptr) {
            return this.baseType.isSame(ptr.baseType);
        }
        return false;
    }

    @Override
    public boolean isCastableTo(Type other) {
        return isSame(other);
    }

    @Override
    public String toString() {
        return baseType.toString() + "*";
    }
}
