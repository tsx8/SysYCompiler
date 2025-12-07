package top.tsxb.compiler.ir.value;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.NoneType;

public class Function extends User {
    public final boolean isBuiltin;
    private final List<BasicBlock> basicBlocks = new LinkedList<>();
    private final List<Argument> arguments = new LinkedList<>();
    private final Map<String, Integer> valueNameMap = new HashMap<>();

    public Function(String name, FuncType type, boolean builtin) {
        super(type, name);
        isBuiltin = builtin;
        for (IrType paramType : type.getParamTypes()) {
            Argument arg = new Argument(paramType, "arg", this);
            arguments.add(arg);
            resolveName(arg);
        }
    }

    public void addBasicBlock(BasicBlock basicBlock) {
        basicBlocks.add(basicBlock);
        resolveName(basicBlock);
    }

    public void resolveName(Value value) {
        boolean needsName = (value instanceof BasicBlock) || !(value.getType() instanceof NoneType);
        if (!needsName) {
            return;
        }
        String nameHint = value.getName();
        if (nameHint == null || nameHint.isEmpty()) {
            nameHint = "anonymous";
            value.setName(nameHint);
        }
        if (valueNameMap.containsKey(nameHint)) {
            int count = valueNameMap.get(nameHint);
            value.setName(nameHint + "." + count);
            valueNameMap.put(nameHint, count + 1);
        } else {
            valueNameMap.put(nameHint, 1);
        }
    }

    public List<BasicBlock> getBasicBlocks() {
        return basicBlocks;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    @Override
    public String getRef() {
        return "@" + getName();
    }

    @Override
    public String toString() {
        FuncType funcType = (FuncType)type;
        StringBuilder sb = new StringBuilder();
        if (isBuiltin) {
            sb.append("declare ");
        } else {
            sb.append("define ");
        }
        sb.append(funcType.getReturnType()).append(" ").append(getRef()).append("(");
        sb.append(arguments.stream().map(Argument::toString).collect(Collectors.joining(", ")));
        sb.append(")");
        if (isBuiltin) {
            return sb.toString();
        }
        sb.append(" {").append(System.lineSeparator());
        for (BasicBlock basicBlock : basicBlocks) {
            sb.append(basicBlock);
        }
        sb.append("}").append(System.lineSeparator());
        return sb.toString();
    }
}
