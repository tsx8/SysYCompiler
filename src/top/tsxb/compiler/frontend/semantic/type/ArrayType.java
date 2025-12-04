package top.tsxb.compiler.frontend.semantic.type;

import java.util.Objects;

public record ArrayType(Type elementType, int length) implements Type {
    public static final int UNSIZED = -1;

    public ArrayType {
        Objects.requireNonNull(elementType);
    }

    public boolean isUnsized() {
        return UNSIZED == length;
    }

    @Override
    public boolean isSame(Type other) {
        return other instanceof ArrayType at && this.elementType.isSame(at.elementType) && this.length == at.length;
    }

    @Override
    public boolean isCastableTo(Type other) {
        if (!(other instanceof ArrayType at)) {
            return false;
        }

        if (!this.elementType.isSame(at.elementType)) {
            return false;
        }

        // if the target is unsized, any length is acceptable
        if (at.isUnsized()) {
            return true;
        }

        return this.length == at.length;
    }

    @Override
    public String toString() {
        if (isUnsized()) {
            return elementType + "[]";
        }
        return elementType + "[" + length + "]";
    }
}
