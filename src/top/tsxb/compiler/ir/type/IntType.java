package top.tsxb.compiler.ir.type;

public class IntType extends IrType {
    public static final IntType I32 = new IntType(32);
    public static final IntType I8 = new IntType(8);
    public static final IntType I1 = new IntType(1);
    private final int bitWidth;

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
}
