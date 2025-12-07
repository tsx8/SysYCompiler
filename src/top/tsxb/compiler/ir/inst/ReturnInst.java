package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.base.Value;

public class ReturnInst extends Instruction {
    public ReturnInst(Value retVal, BasicBlock parent) {
        super(NoneType.VOID, OpCode.RET, "", parent);
        addOperand(retVal);
    }

    public ReturnInst(BasicBlock parent) {
        super(NoneType.VOID, OpCode.RET, "", parent);
    }

    @Override
    public String toString() {
        if (getNumOperands() == 0) {
            return "ret void";
        }
        Value retVal = getOperand(0);
        return String.format("ret %s %s", retVal.getType(), retVal.getRef());
    }
}
