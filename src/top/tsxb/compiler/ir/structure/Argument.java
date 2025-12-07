package top.tsxb.compiler.ir.structure;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.base.Value;

public class Argument extends Value {
    private final Function parent;

    public Argument(IrType type, String name, Function parent) {
        super(type, name);
        this.parent = parent;
    }

    public Function getParent() {
        return parent;
    }

    @Override
    public String getRef() {
        return "%" + getName();
    }

    @Override
    public String toString() {
        return getType() + " " + getRef();
    }
}
