package top.tsxb.compiler.frontend.semantic.type;

import java.util.Objects;

public record ArrayType(Type elementType, int numElements) implements Type {
    public ArrayType {
        Objects.requireNonNull(elementType);
    }

    @Override
    public boolean isSame(Type other) {
        if (other instanceof ArrayType at) {
            return this.numElements == at.numElements && this.elementType.isSame(at.elementType);
        }
        return false;
    }

    @Override
    public boolean isCastableTo(Type other) {
        if (isSame(other)) {
            return true;
        }

        if (other instanceof PointerType ptr) {
            return this.elementType.isSame(ptr.baseType());
        }

        return false;
    }

    @Override
    public String toString() {
        return elementType.toString() + "[" + numElements + "]";
    }
}
