package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.value.Value;
import top.tsxb.compiler.ir.value.BasicBlock;

public class BinaryInst extends Instruction {
    public BinaryInst(OpCode op, Value left, Value right, BasicBlock parent) {
        super(left.getType(), op, "", parent); // temporarily set name to ""
        addOperand(left);
        addOperand(right);
    }

    @Override
    public String toString() {
        Value left = getOperand(0);
        Value right = getOperand(1);
        return String.format("%s = %s %s %s, %s", name, getOpCode().toString(), type.toString(), left.getName(),
            right.getName());
    }
}
