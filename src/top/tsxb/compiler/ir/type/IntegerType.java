package top.tsxb.compiler.ir.type;

public final class IntegerType implements Type {
    private static final IntegerType instance = new IntegerType();

    private IntegerType() {}

    public static IntegerType getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "i32";
    }
}
