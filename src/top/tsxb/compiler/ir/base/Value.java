package top.tsxb.compiler.ir.base;

import java.util.ArrayList;
import java.util.Iterator;
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

    public synchronized void addUse(Use use) {
        useList.add(use);
    }

    public synchronized void removeUseOf(User user) {
        for (Iterator<Use> iterator = useList.iterator(); iterator.hasNext();) {
            Use use = iterator.next();
            if (use.user() == user) {
                iterator.remove();
                break;
            }
        }
    }

    public synchronized void replaceAllUsesWith(Value replacement) {
        var usesSnapshot = new ArrayList<>(useList);
        for (Use use : usesSnapshot) {
            use.user().replaceOperand(this, replacement);
        }
        useList.clear();
    }

    public synchronized boolean hasUses() {
        return !useList.isEmpty();
    }

    public abstract String getRef();

    @Override
    public abstract String toString();
}
