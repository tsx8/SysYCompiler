package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.PtrType;
import top.tsxb.compiler.ir.value.BasicBlock;

public class AllocaInst extends Instruction {
    private final IrType allocatedType;

    public AllocaInst(IrType allocatedType, String name, BasicBlock parent) {
        super(new PtrType(allocatedType), OpCode.ALLOCA, name, parent);
        this.allocatedType = allocatedType;
    }

    @Override
    public String toString() {
        return String.format("%s = alloca %s", getRef(), allocatedType);
    }
}
