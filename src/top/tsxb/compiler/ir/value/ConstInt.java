package top.tsxb.compiler.ir.value;

import top.tsxb.compiler.ir.type.IntType;

public class ConstInt extends Constant {
    public static final ConstInt ZERO = new ConstInt(IntType.I32, 0);
    private final int value;

    public ConstInt(IntType type, int value) {
        super(type, "");
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String getRef() {
        return String.valueOf(value);
    }
}
