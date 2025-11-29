package top.tsxb.compiler.ir.type;

public class IntType extends IrType {
    private final int bitWidth;

    public static final IntType I32 = new IntType(32);
    public static final IntType I8 = new IntType(8);
    public static final IntType I1 = new IntType(1);

    private IntType(int bitWidth) {
        this.bitWidth = bitWidth;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    @Override
    public String toString() {
        return "i" + bitWidth;
    }

    @Override
    public boolean isInteger() {
        return true;
    }
}
