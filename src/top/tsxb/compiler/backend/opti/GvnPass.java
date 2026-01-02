package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;

import java.util.*;

public class GvnPass implements Pass {
    private final Map<Value, Value> replacementMap = new IdentityHashMap<>();

    @Override
    public boolean run(Module module) {
        boolean changed = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration()) continue;
            replacementMap.clear();
            changed |= runOnFunction(function);
        }
        return changed;
    }

    private Value getCanonical(Value v) {
        while (replacementMap.containsKey(v)) {
            v = replacementMap.get(v);
        }
        return v;
    }

    private boolean runOnFunction(Function function) {
        if (function.getBasicBlocks().isEmpty()) return false;
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);
        return runOnDomTree(function.getBasicBlocks().get(0), new HashMap<>(), new HashMap<>(), domInfo);
    }

    private boolean runOnDomTree(BasicBlock bb, Map<GvnKey, Instruction> valueTable, Map<Value, Value> memoryTable, DominatorAnalysis.DominatorInfo domInfo) {
        boolean changed = false;
        Map<GvnKey, Instruction> localTable = new HashMap<>(valueTable);
        Map<Value, Value> localMemoryTable = new HashMap<>(memoryTable);
        
        Iterator<Instruction> it = bb.getInstructions().iterator();
        while (it.hasNext()) {
            Instruction inst = it.next();

            // Memory Forwarding
            if (inst.getOpCode() == OpCode.LOAD) {
                Value ptr = getCanonical(inst.getOperand(0));
                if (localMemoryTable.containsKey(ptr)) {
                    Value existing = getCanonical(localMemoryTable.get(ptr));
                    inst.replaceAllUsesWith(existing);
                    replacementMap.put(inst, existing);
                    it.remove();
                    changed = true;
                    continue;
                } else {
                    localMemoryTable.put(ptr, inst);
                }
            } else if (inst.getOpCode() == OpCode.STORE) {
                localMemoryTable.clear();
                Value val = getCanonical(inst.getOperand(0));
                Value ptr = getCanonical(inst.getOperand(1));
                localMemoryTable.put(ptr, val);
            } else if (inst.getOpCode() == OpCode.CALL) {
                localMemoryTable.clear();
            }

            if (inst.isPinned()) continue;

            GvnKey key = new GvnKey(inst, this);
            if (localTable.containsKey(key)) {
                Instruction existing = localTable.get(key);
                Value canonicalExisting = getCanonical(existing);
                inst.replaceAllUsesWith(canonicalExisting);
                replacementMap.put(inst, canonicalExisting);
                it.remove();
                changed = true;
            } else {
                localTable.put(key, inst);
            }
        }

        for (BasicBlock child : domInfo.domTree().getOrDefault(bb, List.of())) {
            changed |= runOnDomTree(child, localTable, localMemoryTable, domInfo);
        }
        return changed;
    }

    private static class GvnKey {
        private final OpCode op;
        private final Object extra;
        private final List<Value> operands;

        public GvnKey(Instruction inst, GvnPass pass) {
            this.op = inst.getOpCode();
            this.operands = new ArrayList<>();
            for (int i = 0; i < inst.getNumOperands(); i++) {
                operands.add(pass.getCanonical(inst.getOperand(i)));
            }
            
            if (inst instanceof IcmpInst icmp) {
                this.extra = icmp.getPredicate();
            } else {
                this.extra = null;
            }

            if (isCommutative(inst) && operands.size() == 2) {
                Value v1 = operands.get(0);
                Value v2 = operands.get(1);
                if (System.identityHashCode(v1) > System.identityHashCode(v2)) {
                    operands.set(0, v2);
                    operands.set(1, v1);
                }
            }
        }

        private boolean isCommutative(Instruction inst) {
            OpCode op = inst.getOpCode();
            if (op == OpCode.ADD || op == OpCode.MUL) return true;
            if (inst instanceof IcmpInst icmp) {
                IcmpInst.CondCode pred = icmp.getPredicate();
                return pred == IcmpInst.CondCode.EQ || pred == IcmpInst.CondCode.NE;
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GvnKey gvnKey = (GvnKey) o;
            return op == gvnKey.op && Objects.equals(extra, gvnKey.extra) && Objects.equals(operands, gvnKey.operands);
        }

        @Override
        public int hashCode() {
            return Objects.hash(op, extra, operands);
        }
    }
}
