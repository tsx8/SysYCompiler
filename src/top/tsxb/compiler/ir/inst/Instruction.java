package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.base.User;

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

    public boolean isPinned() {
        return switch (opCode) {
            case RET, BR, STORE, PHI, CALL, ALLOCA, LOAD -> true;
            default -> false;
        };
    }

    @Override
    public String getRef() {
        return "%" + getName();
    }
}
