package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.structure.BasicBlock;

public class BinaryInst extends Instruction {
    public BinaryInst(OpCode op, Value left, Value right, BasicBlock parent) {
        super(left.getType(), op, op.toString().toLowerCase(), parent);
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
