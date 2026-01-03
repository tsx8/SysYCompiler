package top.tsxb.compiler.backend.opti;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.constant.ConstZero;
import top.tsxb.compiler.ir.inst.AllocaInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.LoadInst;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.inst.StoreInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.type.IrType;

public class Mem2RegPass implements Pass {

    @Override
    public boolean run(Module module) {
        boolean changed = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration()) {
                continue;
            }
            if (runOnFunction(function)) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean runOnFunction(Function function) {
        List<BasicBlock> blocks = function.getBasicBlocks();
        if (blocks.isEmpty()) {
            return false;
        }
        BasicBlock entry = blocks.get(0);
        Map<AllocaInst, VarInfo> candidates = collectAllocas(entry);
        if (candidates.isEmpty()) {
            return false;
        }
        analyzeVariableUsage(candidates, blocks);
        Set<AllocaInst> promotable = candidates.values().stream().filter(VarInfo::isPromotable).map(info -> info.alloca)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (promotable.isEmpty()) {
            return false;
        }
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);
        Map<BasicBlock, Map<AllocaInst, PhiInst>> phiMap =
            insertPhiNodes(candidates, promotable, domInfo.dominanceFrontier());
        Map<AllocaInst, Deque<Value>> stacks = new LinkedHashMap<>();
        rename(entry, stacks, cfg.successors(), domInfo.domTree(), phiMap, candidates, promotable);
        cleanupAllocas(promotable);
        return true;
    }

    private Map<AllocaInst, VarInfo> collectAllocas(BasicBlock entry) {
        Map<AllocaInst, VarInfo> allocas = new LinkedHashMap<>();
        for (Instruction instruction : entry.getInstructions()) {
            if (instruction instanceof AllocaInst alloca && isPromotableType(alloca.getAllocatedType())) {
                allocas.put(alloca, new VarInfo(alloca));
            }
        }
        return allocas;
    }

    private boolean isPromotableType(IrType type) {
        return type instanceof IntType;
    }

    private void analyzeVariableUsage(Map<AllocaInst, VarInfo> vars, List<BasicBlock> blocks) {
        for (BasicBlock block : blocks) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof StoreInst store) {
                    Value ptr = store.getOperand(1);
                    if (ptr instanceof AllocaInst alloca && vars.containsKey(alloca)) {
                        vars.get(alloca).defBlocks.add(block);
                    }
                }
                for (int i = 0; i < instruction.getNumOperands(); i++) {
                    Value operand = instruction.getOperand(i);
                    if (operand instanceof AllocaInst alloca && vars.containsKey(alloca)) {
                        boolean isPointerUse =
                            (instruction instanceof LoadInst && i == 0) || (instruction instanceof StoreInst && i == 1);
                        if (!isPointerUse) {
                            vars.get(alloca).promotable = false;
                        }
                    }
                }
            }
        }
    }

    private Map<BasicBlock, Map<AllocaInst, PhiInst>> insertPhiNodes(Map<AllocaInst, VarInfo> vars,
        Set<AllocaInst> promotable, Map<BasicBlock, Set<BasicBlock>> dominanceFrontier) {
        Map<BasicBlock, Map<AllocaInst, PhiInst>> phiMap = new LinkedHashMap<>();
        for (AllocaInst alloca : promotable) {
            VarInfo info = vars.get(alloca);
            if (info == null) {
                continue;
            }
            Queue<BasicBlock> workQueue = new ArrayDeque<>(info.defBlocks);
            Set<BasicBlock> processed = new LinkedHashSet<>(info.defBlocks);
            Set<BasicBlock> hasPhi = new LinkedHashSet<>();
            while (!workQueue.isEmpty()) {
                BasicBlock block = workQueue.poll();
                for (BasicBlock frontier : dominanceFrontier.getOrDefault(block, Set.of())) {
                    if (!hasPhi.contains(frontier)) {
                        PhiInst phi = new PhiInst(info.valueType, alloca.getName(), null);
                        frontier.addFirst(phi);
                        phiMap.computeIfAbsent(frontier, bb -> new LinkedHashMap<>()).put(alloca, phi);
                        hasPhi.add(frontier);
                        if (!processed.contains(frontier)) {
                            processed.add(frontier);
                            workQueue.add(frontier);
                        }
                    }
                }
            }
        }
        return phiMap;
    }

    private void rename(BasicBlock block, Map<AllocaInst, Deque<Value>> stacks,
        Map<BasicBlock, List<BasicBlock>> successors, Map<BasicBlock, List<BasicBlock>> domTree,
        Map<BasicBlock, Map<AllocaInst, PhiInst>> phiMap, Map<AllocaInst, VarInfo> vars, Set<AllocaInst> promotable) {
        Map<AllocaInst, Integer> pushCounts = new LinkedHashMap<>();
        Map<AllocaInst, PhiInst> blockPhis = phiMap.getOrDefault(block, Map.of());
        for (Map.Entry<AllocaInst, PhiInst> entry : blockPhis.entrySet()) {
            AllocaInst alloca = entry.getKey();
            PhiInst phi = entry.getValue();
            Deque<Value> stack = stacks.computeIfAbsent(alloca, key -> new ArrayDeque<>());
            stack.push(phi);
            pushCounts.merge(alloca, 1, Integer::sum);
        }
        ListIterator<Instruction> iterator = block.getInstructions().listIterator();
        while (iterator.hasNext()) {
            Instruction inst = iterator.next();
            if (inst instanceof AllocaInst alloca && promotable.contains(alloca)) {
                continue;
            }
            if (inst instanceof StoreInst store) {
                Value ptr = store.getOperand(1);
                if (ptr instanceof AllocaInst alloca && promotable.contains(alloca)) {
                    Deque<Value> stack = stacks.computeIfAbsent(alloca, key -> new ArrayDeque<>());
                    stack.push(store.getOperand(0));
                    pushCounts.merge(alloca, 1, Integer::sum);
                    store.dropAllReferences();
                    iterator.remove();
                }
                continue;
            }
            if (inst instanceof LoadInst load) {
                Value ptr = load.getOperand(0);
                if (ptr instanceof AllocaInst alloca && promotable.contains(alloca)) {
                    Deque<Value> stack = stacks.get(alloca);
                    Value replacement = (stack != null) ? stack.peek() : null;
                    if (replacement == null) {
                        replacement = defaultValue(alloca, vars);
                    }
                    load.replaceAllUsesWith(replacement);
                    load.dropAllReferences();
                    iterator.remove();
                }
            }
        }
        for (BasicBlock succ : successors.getOrDefault(block, List.of())) {
            Map<AllocaInst, PhiInst> phis = phiMap.get(succ);
            if (phis == null) {
                continue;
            }
            for (Map.Entry<AllocaInst, PhiInst> entry : phis.entrySet()) {
                AllocaInst alloca = entry.getKey();
                if (!promotable.contains(alloca)) {
                    continue;
                }
                Deque<Value> stack = stacks.get(alloca);
                Value incoming = (stack != null) ? stack.peek() : null;
                if (incoming == null) {
                    incoming = defaultValue(alloca, vars);
                }
                entry.getValue().setIncoming(block, incoming);
            }
        }
        for (BasicBlock child : domTree.getOrDefault(block, List.of())) {
            rename(child, stacks, successors, domTree, phiMap, vars, promotable);
        }
        for (Map.Entry<AllocaInst, Integer> entry : pushCounts.entrySet()) {
            Deque<Value> stack = stacks.get(entry.getKey());
            if (stack == null) {
                continue;
            }
            for (int i = 0; i < entry.getValue(); i++) {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            }
        }
    }

    private void cleanupAllocas(Set<AllocaInst> promotable) {
        for (AllocaInst alloca : promotable) {
            if (!alloca.hasUses()) {
                BasicBlock parent = alloca.getParent();
                if (parent != null) {
                    parent.getInstructions().remove(alloca);
                }
            }
        }
    }

    private Value zeroValue(IrType type) {
        if (type instanceof IntType intType) {
            return new ConstInt(intType, 0);
        }
        return new ConstZero(type);
    }

    private Value defaultValue(AllocaInst alloca, Map<AllocaInst, VarInfo> vars) {
        VarInfo info = vars.get(alloca);
        IrType type = (info != null) ? info.valueType : IntType.I32;
        return zeroValue(type);
    }

    private static class VarInfo {
        final AllocaInst alloca;
        final IrType valueType;
        final Set<BasicBlock> defBlocks = new LinkedHashSet<>();
        boolean promotable = true;

        VarInfo(AllocaInst alloca) {
            this.alloca = alloca;
            this.valueType = alloca.getAllocatedType();
        }

        boolean isPromotable() {
            return promotable && !defBlocks.isEmpty();
        }
    }
}
