package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.Value;

public class StoreInst extends Instruction {
    public StoreInst(Value value, Value ptr, BasicBlock parent) {
        super(NoneType.VOID, OpCode.STORE, "", parent);
        addOperand(value);
        addOperand(ptr);
    }

    @Override
    public String toString() {
        Value val = getOperand(0);
        Value ptr = getOperand(1);
        return String.format("store %s %s, %s %s", val.getType(), val.getRef(), ptr.getType(), ptr.getRef());
    }
}
