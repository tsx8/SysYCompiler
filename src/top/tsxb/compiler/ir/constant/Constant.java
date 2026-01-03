package top.tsxb.compiler.ir.constant;

import top.tsxb.compiler.ir.base.User;
import top.tsxb.compiler.ir.type.IrType;

public abstract class Constant extends User {
    public Constant(IrType type, String name) {
        super(type, name);
    }

    @Override
    public String toString() {
        return getRef();
    }
}
