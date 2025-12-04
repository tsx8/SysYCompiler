package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.User;

public abstract class Instruction extends User {
    private final OpCode opCode;
    private BasicBlock parent;

    public Instruction(IrType type, OpCode code, String name, BasicBlock parent) {
        super(type, name);
        this.opCode = code;
        this.parent = parent;
        if (this.parent != null) {
            parent.addInstruction(this);
        }
    }

    public BasicBlock getParent() {
        return parent;
    }

    public void setParent(BasicBlock parent) {
        this.parent = parent;
    }

    public OpCode getOpCode() {
        return opCode;
    }

    @Override
    public String getRef() {
        return "%" + getName();
    }
}
