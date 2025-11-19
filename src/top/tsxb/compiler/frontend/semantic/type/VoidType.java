package top.tsxb.compiler.frontend.semantic.type;

public final class VoidType implements Type {
    private static final VoidType instance = new VoidType();

    private VoidType() {}

    public static VoidType getInstance() {
        return instance;
    }

    @Override
    public boolean isSame(Type other) {
        return other instanceof VoidType;
    }

    @Override
    public boolean isCastableTo(Type other) {
        return false;
    }

    @Override
    public String toString() {
        return "void";
    }
}
