package top.tsxb.compiler.ir.value;

import top.tsxb.compiler.ir.type.IrType;

public abstract class Constant extends User {
    public Constant(IrType type, String name) {
        super(type, name);
    }
}
