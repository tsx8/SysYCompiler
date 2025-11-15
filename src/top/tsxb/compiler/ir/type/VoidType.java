package top.tsxb.compiler.ir.type;

public final class VoidType implements Type {
    private static final VoidType instance = new VoidType();

    private VoidType() {}

    public static VoidType getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "void";
    }
}
