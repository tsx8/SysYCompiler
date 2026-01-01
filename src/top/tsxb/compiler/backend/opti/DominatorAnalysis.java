package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.BrInst;

import java.util.*;

public class DominatorAnalysis {
    public record Cfg(Map<BasicBlock, List<BasicBlock>> successors, Map<BasicBlock, List<BasicBlock>> predecessors) {
    }

    public record DominatorInfo(Map<BasicBlock, Set<BasicBlock>> dominators, Map<BasicBlock, BasicBlock> idom,
                                Map<BasicBlock, List<BasicBlock>> domTree, Map<BasicBlock, Set<BasicBlock>> dominanceFrontier) {
    }

    public static Cfg computeCfg(Function function) {
        Map<BasicBlock, List<BasicBlock>> successors = new LinkedHashMap<>();
        Map<BasicBlock, List<BasicBlock>> predecessors = new LinkedHashMap<>();

        for (BasicBlock bb : function.getBasicBlocks()) {
            successors.put(bb, new ArrayList<>());
            predecessors.putIfAbsent(bb, new ArrayList<>());
        }

        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = bb.getInstructions();
            if (insts.isEmpty()) continue;
            Instruction last = insts.get(insts.size() - 1);
            if (last instanceof BrInst br) {
                if (br.getNumOperands() == 3) {
                    addEdge(bb, (BasicBlock) br.getOperand(1), successors, predecessors);
                    addEdge(bb, (BasicBlock) br.getOperand(2), successors, predecessors);
                } else {
                    addEdge(bb, (BasicBlock) br.getOperand(0), successors, predecessors);
                }
            }
        }
        return new Cfg(successors, predecessors);
    }

    private static void addEdge(BasicBlock from, BasicBlock to, Map<BasicBlock, List<BasicBlock>> successors, Map<BasicBlock, List<BasicBlock>> predecessors) {
        successors.get(from).add(to);
        predecessors.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
    }

    public static DominatorInfo computeDominators(Function function) {
        BasicBlock entry = function.getBasicBlocks().get(0);
        Cfg cfg = computeCfg(function);
        List<BasicBlock> rpo = getReversePostOrder(entry, cfg.successors());
        Map<BasicBlock, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < rpo.size(); i++) {
            order.put(rpo.get(i), i);
        }

        Map<BasicBlock, BasicBlock> idom = new LinkedHashMap<>();
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

        Map<BasicBlock, Set<BasicBlock>> dominators = new LinkedHashMap<>();
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
            }
            dominators.put(block, domSet);
        }

        Map<BasicBlock, List<BasicBlock>> domTree = new LinkedHashMap<>();
        for (BasicBlock block : rpo) {
            if (block == entry) {
                continue;
            }
            BasicBlock parent = idom.get(block);
            if (parent != null && parent != block) {
                domTree.computeIfAbsent(parent, key -> new ArrayList<>()).add(block);
            }
        }

        Map<BasicBlock, Set<BasicBlock>> frontier = new LinkedHashMap<>();
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
                while (runner != idom.get(block)) {
                    frontier.get(runner).add(block);
                    if (runner == entry) {
                        break;
                    }
                    runner = idom.get(runner);
                }
            }
        }
        return new DominatorInfo(dominators, idom, domTree, frontier);
    }

    private static List<BasicBlock> getReversePostOrder(BasicBlock entry, Map<BasicBlock, List<BasicBlock>> successors) {
        List<BasicBlock> postOrder = new ArrayList<>();
        Set<BasicBlock> visited = new LinkedHashSet<>();
        dfsPostOrder(entry, successors, visited, postOrder);
        Collections.reverse(postOrder);
        return postOrder;
    }

    private static void dfsPostOrder(BasicBlock block, Map<BasicBlock, List<BasicBlock>> successors, Set<BasicBlock> visited,
                                     List<BasicBlock> postOrder) {
        visited.add(block);
        for (BasicBlock succ : successors.getOrDefault(block, List.of())) {
            if (!visited.contains(succ)) {
                dfsPostOrder(succ, successors, visited, postOrder);
            }
        }
        postOrder.add(block);
    }

    private static BasicBlock intersect(Map<BasicBlock, BasicBlock> idom, Map<BasicBlock, Integer> order,
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
}
