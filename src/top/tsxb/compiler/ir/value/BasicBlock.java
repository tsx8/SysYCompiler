package top.tsxb.compiler.ir.value;

import java.util.LinkedList;
import java.util.List;

import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.type.NoneType;

public class BasicBlock extends Value {
    private final Function parent;
    private final List<Instruction> instructions = new LinkedList<>();

    public BasicBlock(Function parent) {
        this("", parent);
    }

    public BasicBlock(String name, Function parent) {
        super(NoneType.VOID, name);
        this.parent = parent;
        if (parent != null) {
            parent.addBasicBlock(this);
        }
    }

    public void addInstruction(Instruction instruction) {
        instructions.add(instruction);
        instruction.setParent(this);
    }

    public void addFirst(Instruction instruction) {
        instructions.add(0, instruction);
        instruction.setParent(this);
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public Function getParent() {
        return parent;
    }

    @Override
    public String getRef() {
        return "%" + getName();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(":").append(System.lineSeparator());
        for (Instruction instruction : instructions) {
            sb.append("  ").append(instruction).append(System.lineSeparator());
        }
        return sb.toString();
    }

}
