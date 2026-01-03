package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.GetElementPtrInst;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.OpCode;
import top.tsxb.compiler.ir.structure.Argument;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalVariable;

public class GvnPass implements Pass {
    private final Map<Value, Value> replacementMap = new IdentityHashMap<>();
    private final Set<Function> pureFunctions = new LinkedHashSet<>();

    @Override
    public boolean run(top.tsxb.compiler.ir.structure.Module module) {
        analyzePureFunctions(module);
        boolean changed = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration())
                continue;
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
        if (function.getBasicBlocks().isEmpty())
            return false;
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);
        return runOnDomTree(function.getBasicBlocks().get(0), new LinkedHashMap<>(), new LinkedHashMap<>(), domInfo);
    }

    private boolean runOnDomTree(BasicBlock bb, Map<GvnKey, Instruction> valueTable, Map<Value, Value> memoryTable,
        DominatorAnalysis.DominatorInfo domInfo) {
        boolean changed = false;
        Map<GvnKey, Instruction> localTable = new LinkedHashMap<>(valueTable);
        Map<Value, Value> localMemoryTable = new LinkedHashMap<>(memoryTable);

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
                if (!isPureCall(inst)) {
                    localMemoryTable.clear();
                }
            }

            if (inst.isPinned() && !isPureCall(inst))
                continue;

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

    private void analyzePureFunctions(top.tsxb.compiler.ir.structure.Module module) {
        pureFunctions.clear();
        Set<Function> nonPure = new LinkedHashSet<>();
        Map<Function, Set<Function>> callGraph = new LinkedHashMap<>();

        for (Function func : module.getFunctionList()) {
            if (func.isDeclaration()) {
                nonPure.add(func);
                continue;
            }

            boolean sideEffect = false;
            Set<Function> callees = new LinkedHashSet<>();
            for (BasicBlock bb : func.getBasicBlocks()) {
                for (Instruction inst : bb.getInstructions()) {
                    if (inst.getOpCode() == OpCode.STORE) {
                        Value ptr = inst.getOperand(1);
                        if (isExternalPointer(ptr)) {
                            sideEffect = true;
                            break;
                        }
                    } else if (inst.getOpCode() == OpCode.LOAD) {
                        Value ptr = inst.getOperand(0);
                        if (isExternalPointer(ptr)) {
                            if (isVolatilePointer(ptr)) {
                                sideEffect = true;
                                break;
                            }
                        }
                    } else if (inst.getOpCode() == OpCode.CALL) {
                        callees.add((Function)inst.getOperand(0));
                    }
                }
                if (sideEffect)
                    break;
            }

            if (sideEffect) {
                nonPure.add(func);
            } else {
                callGraph.put(func, callees);
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Function func : new ArrayList<>(callGraph.keySet())) {
                for (Function callee : callGraph.get(func)) {
                    if (nonPure.contains(callee)) {
                        nonPure.add(func);
                        callGraph.remove(func);
                        changed = true;
                        break;
                    }
                }
            }
        }

        for (Function func : module.getFunctionList()) {
            if (!nonPure.contains(func)) {
                pureFunctions.add(func);
            }
        }
    }

    private boolean isExternalPointer(Value ptr) {
        Value base = getBase(ptr);
        return base instanceof GlobalVariable || base instanceof Argument;
    }

    private boolean isVolatilePointer(Value ptr) {
        Value base = getBase(ptr);
        if (base instanceof GlobalVariable gv) {
            return !gv.isConst();
        }
        return base instanceof Argument;
    }

    private Value getBase(Value v) {
        while (v instanceof GetElementPtrInst gep) {
            v = gep.getOperand(0);
        }
        return v;
    }

    private boolean isPureCall(Instruction inst) {
        if (inst.getOpCode() == OpCode.CALL) {
            return pureFunctions.contains((Function)inst.getOperand(0));
        }
        return false;
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
            if (op == OpCode.ADD || op == OpCode.MUL)
                return true;
            if (inst instanceof IcmpInst icmp) {
                IcmpInst.CondCode pred = icmp.getPredicate();
                return pred == IcmpInst.CondCode.EQ || pred == IcmpInst.CondCode.NE;
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            GvnKey gvnKey = (GvnKey)o;
            return op == gvnKey.op && Objects.equals(extra, gvnKey.extra) && Objects.equals(operands, gvnKey.operands);
        }

        @Override
        public int hashCode() {
            return Objects.hash(op, extra, operands);
        }
    }
}
