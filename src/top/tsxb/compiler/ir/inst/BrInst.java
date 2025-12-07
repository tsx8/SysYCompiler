package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.base.Value;

public class BrInst extends Instruction {
    public BrInst(BasicBlock dest, BasicBlock parent) {
        super(NoneType.VOID, OpCode.BR, "", parent);
        addOperand(dest);
    }

    public BrInst(Value cond, BasicBlock ifTrue, BasicBlock ifFalse, BasicBlock parent) {
        super(NoneType.VOID, OpCode.BR, "", parent);
        addOperand(cond);
        addOperand(ifTrue);
        addOperand(ifFalse);
    }

    @Override
    public String toString() {
        if (getNumOperands() == 3) {
            Value cond = getOperand(0);
            Value ifTrue = getOperand(1);
            Value ifFalse = getOperand(2);
            return String.format("br i1 %s, label %s, label %s", cond.getRef(), ifTrue.getRef(), ifFalse.getRef());
        } else {
            Value dest = getOperand(0);
            return String.format("br label %s", dest.getRef());
        }
    }
}
