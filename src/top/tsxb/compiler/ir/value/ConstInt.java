package top.tsxb.compiler.ir.value;

import top.tsxb.compiler.ir.type.IntType;

public class ConstInt extends Constant {
    private final int value;

    public static final ConstInt ZERO = new ConstInt(IntType.I32, 0);

    public ConstInt(IntType type, int value) {
        super(type, String.valueOf(value)); // name 暂时以数值字符串代替？
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
