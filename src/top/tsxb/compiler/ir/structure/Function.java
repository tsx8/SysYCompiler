package top.tsxb.compiler.ir.structure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.NoneType;

public class Function extends GlobalValue {
    private final List<BasicBlock> basicBlocks = new LinkedList<>();
    private final List<Argument> arguments = new LinkedList<>();
    private final Map<String, Integer> localNameMap = new HashMap<>();
    private final Set<String> usedNames = new HashSet<>();

    public Function(String name, FuncType type, Linkage linkage) {
        super(type, name, linkage);
        for (IrType paramType : type.getParamTypes()) {
            Argument arg = new Argument(paramType, "arg", this);
            arguments.add(arg);
            resolveLocalName(arg);
        }
    }

    public Function(String name, FuncType type) {
        this(name, type, Linkage.EXTERNAL);
    }

    public void addBasicBlock(BasicBlock basicBlock) {
        basicBlocks.add(basicBlock);
        resolveLocalName(basicBlock);
    }

    public void removeBasicBlock(BasicBlock basicBlock) {
        basicBlocks.remove(basicBlock);
    }

    public void resolveLocalName(Value value) {
        boolean needsName = (value instanceof BasicBlock) || !(value.getType() instanceof NoneType);
        if (!needsName) {
            return;
        }
        String nameHint = value.getName();
        if (nameHint == null || nameHint.isEmpty()) {
            nameHint = "anonymous";
        }

        String candidate = nameHint;
        if (usedNames.contains(candidate)) {
            int count = localNameMap.getOrDefault(nameHint, 1);
            do {
                candidate = nameHint + "." + count;
                count++;
            } while (usedNames.contains(candidate));
            localNameMap.put(nameHint, count);
        } else {
            if (!localNameMap.containsKey(nameHint)) {
                localNameMap.put(nameHint, 1);
            }
        }
        value.setName(candidate);
        usedNames.add(candidate);
    }

    public List<BasicBlock> getBasicBlocks() {
        return basicBlocks;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    @Override
    public boolean isDeclaration() {
        return basicBlocks.isEmpty();
    }

    @Override
    public String toString() {
        FuncType funcType = (FuncType)getValueType();
        StringBuilder sb = new StringBuilder();
        if (isDeclaration()) {
            sb.append("declare ");
        } else {
            sb.append("define ");
        }
        sb.append(funcType.getReturnType()).append(" ").append(getRef()).append("(");
        sb.append(arguments.stream().map(Argument::toString).collect(Collectors.joining(", ")));
        sb.append(")");
        if (isDeclaration()) {
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
