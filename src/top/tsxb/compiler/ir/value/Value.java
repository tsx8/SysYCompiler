package top.tsxb.compiler.ir.value;

import java.util.ArrayList;
import java.util.List;

import top.tsxb.compiler.ir.Use;
import top.tsxb.compiler.ir.type.IrType;

public abstract class Value {
    protected final IrType type;
    protected String name;

    private final List<Use> useList = new ArrayList<>();

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
}
