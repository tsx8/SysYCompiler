package top.tsxb.compiler.backend.opti;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
import top.tsxb.compiler.ir.inst.BrInst;
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
    private final Map<IrType, Value> zeroValueCache = new HashMap<>();

    @Override
    public void run(Module module) {
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration()) {
                continue;
            }
            runOnFunction(function);
        }
    }

    private void runOnFunction(Function function) {
        List<BasicBlock> blocks = function.getBasicBlocks();
        if (blocks.isEmpty()) {
            return;
        }
        BasicBlock entry = blocks.get(0);
        Map<AllocaInst, VarInfo> candidates = collectAllocas(entry);
        if (candidates.isEmpty()) {
            return;
        }
        analyzeVariableUsage(candidates, blocks);
        Set<AllocaInst> promotable = candidates.values().stream().filter(VarInfo::isPromotable).map(info -> info.alloca)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (promotable.isEmpty()) {
            return;
        }
        Cfg cfg = buildCfg(blocks);
        DominatorInfo domInfo = computeDominators(entry, cfg);
        Map<BasicBlock, Map<AllocaInst, PhiInst>> phiMap =
            insertPhiNodes(candidates, promotable, domInfo.dominanceFrontier());
        Map<AllocaInst, Deque<Value>> stacks = new HashMap<>();
        rename(entry, stacks, cfg.successors(), domInfo.domTree(), phiMap, candidates, promotable);
        cleanupAllocas(promotable);
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
                        boolean isPointerUse = (instruction instanceof LoadInst && i == 0)
                            || (instruction instanceof StoreInst && i == 1);
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
        Map<BasicBlock, Map<AllocaInst, PhiInst>> phiMap = new HashMap<>();
        for (AllocaInst alloca : promotable) {
            VarInfo info = vars.get(alloca);
            if (info == null) {
                continue;
            }
            Queue<BasicBlock> workQueue = new ArrayDeque<>(info.defBlocks);
            Set<BasicBlock> processed = new HashSet<>(info.defBlocks);
            Set<BasicBlock> hasPhi = new HashSet<>();
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
            Map<BasicBlock, Map<AllocaInst, PhiInst>> phiMap, Map<AllocaInst, VarInfo> vars,
            Set<AllocaInst> promotable) {
        Map<AllocaInst, Integer> pushCounts = new HashMap<>();
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
        return zeroValueCache.computeIfAbsent(type, key -> {
            if (key instanceof IntType intType) {
                return new ConstInt(intType, 0);
            }
            return new ConstZero(key);
        });
    }

    private Value defaultValue(AllocaInst alloca, Map<AllocaInst, VarInfo> vars) {
        VarInfo info = vars.get(alloca);
        IrType type = (info != null) ? info.valueType : IntType.I32;
        return zeroValue(type);
    }

    private Cfg buildCfg(List<BasicBlock> blocks) {
        Map<BasicBlock, List<BasicBlock>> successors = new LinkedHashMap<>();
        Map<BasicBlock, List<BasicBlock>> predecessors = new LinkedHashMap<>();
        for (BasicBlock block : blocks) {
            List<BasicBlock> succList = new ArrayList<>();
            Instruction terminator = getTerminator(block);
            if (terminator instanceof BrInst br) {
                if (br.getNumOperands() == 1) {
                    succList.add((BasicBlock)br.getOperand(0));
                } else if (br.getNumOperands() == 3) {
                    succList.add((BasicBlock)br.getOperand(1));
                    succList.add((BasicBlock)br.getOperand(2));
                }
            }
            successors.put(block, succList);
            for (BasicBlock succ : succList) {
                predecessors.computeIfAbsent(succ, key -> new ArrayList<>()).add(block);
            }
            predecessors.putIfAbsent(block, new ArrayList<>());
        }
        return new Cfg(successors, predecessors);
    }

    private Instruction getTerminator(BasicBlock block) {
        List<Instruction> instructions = block.getInstructions();
        if (instructions.isEmpty()) {
            return null;
        }
        return instructions.get(instructions.size() - 1);
    }

    private DominatorInfo computeDominators(BasicBlock entry, Cfg cfg) {
        List<BasicBlock> rpo = getReversePostOrder(entry, cfg.successors());
        Map<BasicBlock, Integer> order = new HashMap<>();
        for (int i = 0; i < rpo.size(); i++) {
            order.put(rpo.get(i), i);
        }

        Map<BasicBlock, BasicBlock> idom = new HashMap<>();
        idom.put(entry, entry);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock block : rpo) {
                if (block == entry) {
                    continue;
                }
                List<BasicBlock> preds = cfg.predecessors().getOrDefault(block, List.of());
                BasicBlock newIdom = null;
                for (BasicBlock pred : preds) {
                    if (!idom.containsKey(pred)) {
                        continue;
                    }
                    if (newIdom == null) {
                        newIdom = pred;
                    } else {
                        newIdom = intersect(idom, order, pred, newIdom);
                    }
                }
                if (newIdom != null && idom.get(block) != newIdom) {
                    idom.put(block, newIdom);
                    changed = true;
                }
            }
        }

        Map<BasicBlock, Set<BasicBlock>> dominators = new HashMap<>();
        for (BasicBlock block : rpo) {
            Set<BasicBlock> domSet = new LinkedHashSet<>();
            if (idom.containsKey(block)) {
                BasicBlock runner = block;
                domSet.add(block);
                while (runner != entry) {
                    runner = idom.get(runner);
                    if (runner == null) {
                        break;
                    }
                    domSet.add(runner);
                }
                domSet.add(entry);
            } else {
                domSet.add(block);
            }
            dominators.put(block, domSet);
        }

        Map<BasicBlock, List<BasicBlock>> domTree = new HashMap<>();
        for (BasicBlock block : rpo) {
            if (block == entry) {
                continue;
            }
            BasicBlock parent = idom.get(block);
            if (parent != null && parent != block) {
                domTree.computeIfAbsent(parent, key -> new ArrayList<>()).add(block);
            }
        }

        Map<BasicBlock, Set<BasicBlock>> frontier = new HashMap<>();
        for (BasicBlock block : rpo) {
            frontier.put(block, new LinkedHashSet<>());
        }
        for (BasicBlock block : rpo) {
            List<BasicBlock> preds = cfg.predecessors().getOrDefault(block, List.of());
            if (preds.size() < 2) {
                continue;
            }
            for (BasicBlock pred : preds) {
                if (!idom.containsKey(pred)) {
                    continue;
                }
                BasicBlock runner = pred;
                while (runner != null && runner != idom.get(block)) {
                    frontier.get(runner).add(block);
                    runner = idom.get(runner);
                }
            }
        }
        return new DominatorInfo(dominators, idom, domTree, frontier);
    }

    private List<BasicBlock> getReversePostOrder(BasicBlock entry, Map<BasicBlock, List<BasicBlock>> successors) {
        List<BasicBlock> postOrder = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();
        dfsPostOrder(entry, successors, visited, postOrder);
        Collections.reverse(postOrder);
        return postOrder;
    }

    private void dfsPostOrder(BasicBlock block, Map<BasicBlock, List<BasicBlock>> successors, Set<BasicBlock> visited,
            List<BasicBlock> postOrder) {
        visited.add(block);
        for (BasicBlock succ : successors.getOrDefault(block, List.of())) {
            if (!visited.contains(succ)) {
                dfsPostOrder(succ, successors, visited, postOrder);
            }
        }
        postOrder.add(block);
    }

    private BasicBlock intersect(Map<BasicBlock, BasicBlock> idom, Map<BasicBlock, Integer> order,
            BasicBlock first, BasicBlock second) {
        BasicBlock finger1 = first;
        BasicBlock finger2 = second;
        while (finger1 != finger2) {
            while (finger1 != null && order.get(finger1) > order.get(finger2)) {
                finger1 = idom.get(finger1);
            }
            while (finger2 != null && order.get(finger2) > order.get(finger1)) {
                finger2 = idom.get(finger2);
            }
            if (finger1 == null || finger2 == null) {
                return null;
            }
        }
        return finger1;
    }

    private record Cfg(Map<BasicBlock, List<BasicBlock>> successors, Map<BasicBlock, List<BasicBlock>> predecessors) {
    }

    private record DominatorInfo(Map<BasicBlock, Set<BasicBlock>> dominators, Map<BasicBlock, BasicBlock> idom,
        Map<BasicBlock, List<BasicBlock>> domTree, Map<BasicBlock, Set<BasicBlock>> dominanceFrontier) {
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
