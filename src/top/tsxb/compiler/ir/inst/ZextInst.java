package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.Value;

public class ZextInst extends Instruction {
    private final IrType destType;

    public ZextInst(Value v, IrType t, BasicBlock p) {
        super(t, OpCode.ZEXT, "", p);
        this.destType = t;
        addOperand(v);
    }

    @Override
    public String toString() {
        Value src = getOperand(0);
        return String.format("%s = zext %s %s to %s", getRef(), src.getType(), src.getRef(), destType);
    }
}
