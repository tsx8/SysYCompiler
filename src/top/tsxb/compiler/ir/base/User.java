package top.tsxb.compiler.ir.base;

import java.util.ArrayList;
import java.util.List;

import top.tsxb.compiler.ir.type.IrType;

public abstract class User extends Value {
    protected final List<Value> operands = new ArrayList<>();

    public User(IrType type, String name) {
        super(type, name);
    }

    public void addOperand(Value operand) {
        operands.add(operand);
        if (operand != null) {
            operand.addUse(new Use(this, operand));
        }
    }

    public Value getOperand(int index) {
        return operands.get(index);
    }

    public int getNumOperands() {
        return operands.size();
    }
}
