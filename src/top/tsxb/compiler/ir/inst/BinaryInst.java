package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.Value;

public class BinaryInst extends Instruction {
    public BinaryInst(OpCode op, Value left, Value right, BasicBlock parent) {
        super(left.getType(), op, "", parent);
        addOperand(left);
        addOperand(right);
    }

    @Override
    public String toString() {
        Value left = getOperand(0);
        Value right = getOperand(1);
        return String.format("%s = %s %s %s, %s", getRef(), getOpCode(), type, left.getRef(), right.getRef());
    }
}
