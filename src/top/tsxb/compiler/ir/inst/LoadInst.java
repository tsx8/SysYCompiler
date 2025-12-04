package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.PtrType;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.Value;

public class LoadInst extends Instruction {
    public LoadInst(Value ptr, BasicBlock parent) {
        super(((PtrType)ptr.getType()).getPointeeType(), OpCode.LOAD, "", parent);
        addOperand(ptr);
    }

    @Override
    public String toString() {
        Value ptr = getOperand(0);
        return String.format("%s = load %s, %s %s", getRef(), type, ptr.getType(), ptr.getRef());
    }
}
