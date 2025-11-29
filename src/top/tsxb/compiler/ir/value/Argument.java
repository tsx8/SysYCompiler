package top.tsxb.compiler.ir.value;

import top.tsxb.compiler.ir.type.IrType;

public class Argument extends Value {
    private final Function parent;
    private final int argNo;

    public Argument(IrType type, String name, Function parent, int argNo) {
        super(type, name);
        this.parent = parent;
        this.argNo = argNo;
    }

    public Function getParent() {
        return parent;
    }

    public int getArgNo() {
        return argNo;
    }

    @Override
    public String toString() {
        return getName();
    }
}
