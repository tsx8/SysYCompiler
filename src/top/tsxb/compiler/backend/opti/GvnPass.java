package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.OpCode;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;

import java.util.*;

public class GvnPass implements Pass {
    @Override
    public boolean run(Module module) {
        boolean changed = false;
        for (Function function : module.getFunctionList()) {
            changed |= runOnFunction(function);
        }
        return changed;
    }

    private boolean runOnFunction(Function function) {
        if (function.getBasicBlocks().isEmpty()) return false;
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);
        return runOnDomTree(function.getBasicBlocks().get(0), new HashMap<>(), domInfo);
    }

    private boolean runOnDomTree(BasicBlock bb, Map<GvnKey, Instruction> valueTable, DominatorAnalysis.DominatorInfo domInfo) {
        boolean changed = false;
        Map<GvnKey, Instruction> localTable = new HashMap<>(valueTable);
        
        Iterator<Instruction> it = bb.getInstructions().iterator();
        while (it.hasNext()) {
            Instruction inst = it.next();
            if (inst.isPinned()) continue;

            GvnKey key = new GvnKey(inst);
            if (localTable.containsKey(key)) {
                Instruction existing = localTable.get(key);
                inst.replaceAllUsesWith(existing);
                it.remove();
                changed = true;
            } else {
                localTable.put(key, inst);
            }
        }

        for (BasicBlock child : domInfo.domTree().getOrDefault(bb, List.of())) {
            changed |= runOnDomTree(child, localTable, domInfo);
        }
        return changed;
    }

    private static class GvnKey {
        private final OpCode op;
        private final Object extra; // For ICMP predicate, etc.
        private final List<Value> operands;

        public GvnKey(Instruction inst) {
            this.op = inst.getOpCode();
            this.operands = new ArrayList<>();
            for (int i = 0; i < inst.getNumOperands(); i++) {
                operands.add(inst.getOperand(i));
            }
            
            if (inst instanceof IcmpInst icmp) {
                this.extra = icmp.getPredicate();
            } else {
                this.extra = null;
            }

            // Handle commutativity
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
