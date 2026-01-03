package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.type.PtrType;

public class LoadInst extends Instruction {
    public LoadInst(Value ptr, BasicBlock parent) {
        super(((PtrType)ptr.getType()).getPointeeType(), OpCode.LOAD, "load", parent);
        addOperand(ptr);
    }

    @Override
    public String toString() {
        Value ptr = getOperand(0);
        return String.format("%s = load %s, %s %s", getRef(), type, ptr.getType(), ptr.getRef());
    }
}
