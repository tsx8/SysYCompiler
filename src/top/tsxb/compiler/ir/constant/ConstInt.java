package top.tsxb.compiler.ir.constant;

import top.tsxb.compiler.ir.type.IntType;

public class ConstInt extends Constant {
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
