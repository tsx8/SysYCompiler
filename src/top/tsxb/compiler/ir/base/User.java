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

    public void setOperand(int index, Value operand) {
        Value previous = operands.set(index, operand);
        if (previous != null) {
            previous.removeUseOf(this);
        }
        if (operand != null) {
            operand.addUse(new Use(this, operand));
        }
    }

    public void replaceOperand(Value oldValue, Value newValue) {
        for (int i = 0; i < operands.size(); i++) {
            if (operands.get(i) == oldValue) {
                setOperand(i, newValue);
            }
        }
    }
}
