package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.value.User;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.value.BasicBlock;

public abstract class Instruction extends User {
    private BasicBlock parent;
    private final OpCode opCode;

    public Instruction(IrType type, OpCode code, String name, BasicBlock parent) {
        super(type, name);
        this.opCode = code;
        this.parent = parent;
        if (this.parent != null) {
            parent.addInstruction(this);
        }
    }

    public void setParent(BasicBlock parent) {
        this.parent = parent;
    }

    public BasicBlock getParent() {
        return parent;
    }

    public OpCode getOpCode() {
        return opCode;
    }

    @Override
    public abstract String toString();
}
