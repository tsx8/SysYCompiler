package top.tsxb.compiler.ir.inst;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.type.IrType;

public class PhiInst extends Instruction {
    private final Map<BasicBlock, Integer> incomingIndex = new LinkedHashMap<>();

    public PhiInst(IrType type, String name, BasicBlock parent) {
        super(type, OpCode.PHI, name, parent);
    }

    public void setIncoming(BasicBlock block, Value value) {
        Integer index = incomingIndex.get(block);
        if (index == null) {
            incomingIndex.put(block, getNumOperands());
            addOperand(value);
        } else {
            setOperand(index, value);
        }
    }

    public void removeIncoming(BasicBlock block) {
        Integer index = incomingIndex.remove(block);
        if (index != null) {
            removeOperand(index);
            // Update indices for remaining blocks
            for (Map.Entry<BasicBlock, Integer> entry : incomingIndex.entrySet()) {
                if (entry.getValue() > index) {
                    entry.setValue(entry.getValue() - 1);
                }
            }
        }
    }

    public Value getIncomingValue(BasicBlock block) {
        Integer index = incomingIndex.get(block);
        return (index != null) ? getOperand(index) : null;
    }

    public Map<BasicBlock, Value> getIncoming() {
        Map<BasicBlock, Value> map = new LinkedHashMap<>();
        for (Map.Entry<BasicBlock, Integer> entry : incomingIndex.entrySet()) {
            map.put(entry.getKey(), getOperand(entry.getValue()));
        }
        return map;
    }

    @Override
    public String toString() {
        String incoming = incomingIndex.entrySet().stream().map(entry -> {
            Value value = getOperand(entry.getValue());
            return String.format("[ %s, %s ]", value.getRef(), entry.getKey().getRef());
        }).collect(Collectors.joining(", "));
        return String.format("%s = phi %s %s", getRef(), type, incoming);
    }
}
