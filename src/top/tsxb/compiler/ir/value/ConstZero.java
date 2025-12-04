package top.tsxb.compiler.ir.value;

import top.tsxb.compiler.ir.type.IrType;

public class ConstZero extends Constant {
    public ConstZero(IrType type) {
        super(type, "");
    }

    @Override
    public String getRef() {
        return "zeroinitializer";
    }
}
