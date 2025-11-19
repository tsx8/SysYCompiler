package top.tsxb.compiler.frontend.semantic.type;

public final class IntegerType implements Type {
    private static final IntegerType instance = new IntegerType();

    private IntegerType() {}

    public static IntegerType getInstance() {
        return instance;
    }

    @Override
    public boolean isSame(Type other) {
        return other instanceof IntegerType;
    }
    
    @Override
    public boolean isCastableTo(Type other) {
        return isSame(other);
    }

    @Override
    public String toString() {
        return "int";
    }
}
