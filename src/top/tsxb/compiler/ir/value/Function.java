package top.tsxb.compiler.ir.value;

import java.util.LinkedList;
import java.util.List;

import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.IrType;

public class Function extends User {
    private final List<BasicBlock> basicBlocks = new LinkedList<>();
    private final List<Argument> arguments = new LinkedList<>();
    private final boolean isBuiltin;

    public Function(String name, FuncType type, boolean builtin) {
        super(type, name);
        isBuiltin = builtin;
        List<IrType> paramTypes = type.getParamTypes();
        for (int i = 0; i < paramTypes.size(); i++) {
            Argument arg = new Argument(paramTypes.get(i), "%arg" + i, this, i);
            arguments.add(arg);
        }
    }

    public String getRawName() {
        return name.startsWith("@") ? name.substring(1) : name;
    }

    public void addBasicBlock(BasicBlock basicBlock) {
        basicBlocks.add(basicBlock);
    }

    public List<BasicBlock> getBasicBlocks() {
        return basicBlocks;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        int count = 0;
        for (BasicBlock bb : basicBlocks) {
            for (Instruction inst : bb.getInstructions()) {
                if (!inst.getType().isVoid()) {
                    if (inst.getName() == null || inst.getName().isEmpty()) {
                        inst.setName("%" + count++);
                    } else if (!inst.getName().startsWith("%")) {
                        inst.setName("%" + inst.getName());
                    }
                }
            }
        }
        FuncType funcType = (FuncType) type;
        StringBuilder sb = new StringBuilder();
        if (isBuiltin) {
            sb.append("declare ");
        } else {
            sb.append("define ");
        }
        sb.append(funcType.getReturnType().toString()).append(" ").append(name).append("(");
        for (int i = 0; i < arguments.size(); i++) {
            Argument arg = arguments.get(i);
            sb.append(arg.getType().toString()).append(" ").append(arg.getName());
            if (i < arguments.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(") ");
        if (isBuiltin) {
            return sb.toString();
        }
        sb.append("{").append(System.lineSeparator());
        for (BasicBlock basicBlock : basicBlocks) {
            sb.append(basicBlock.toString());
        }
        sb.append("}").append(System.lineSeparator());
        return sb.toString();
    }
}
