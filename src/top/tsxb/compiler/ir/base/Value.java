package top.tsxb.compiler.ir.base;

import java.util.ArrayList;
import java.util.List;

import top.tsxb.compiler.ir.type.IrType;

public abstract class Value {
    protected final IrType type;
    private final List<Use> useList = new ArrayList<>();
    protected String name;

    public Value(IrType type, String name) {
        this.type = type;
        this.name = name;
    }

    public IrType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addUse(Use use) {
        useList.add(use);
    }

    public abstract String getRef();

    @Override
    public abstract String toString();
}
