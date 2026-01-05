package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.inst.ReturnInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;

public class SimplifyCfgPass implements Pass {
    private static final class LocalCfg {
        private final Map<BasicBlock, LinkedHashSet<BasicBlock>> successors = new LinkedHashMap<>();
        private final Map<BasicBlock, LinkedHashSet<BasicBlock>> predecessors = new LinkedHashMap<>();

        static LocalCfg build(Function function) {
            LocalCfg cfg = new LocalCfg();
            for (BasicBlock bb : function.getBasicBlocks()) {
                cfg.successors.put(bb, new LinkedHashSet<>());
                cfg.predecessors.put(bb, new LinkedHashSet<>());
            }

            for (BasicBlock bb : function.getBasicBlocks()) {
                BrInst br = findTerminatorBranch(bb);
                if (br == null) {
                    continue;
                }
                if (br.getNumOperands() == 3) {
                    if (br.getOperand(1) instanceof BasicBlock succTrue) {
                        cfg.addEdge(bb, succTrue);
                    }
                    if (br.getOperand(2) instanceof BasicBlock succFalse) {
                        cfg.addEdge(bb, succFalse);
                    }
                } else if (br.getNumOperands() == 1) {
                    if (br.getOperand(0) instanceof BasicBlock succ) {
                        cfg.addEdge(bb, succ);
                    }
                }
            }
            return cfg;
        }

        private static BrInst findTerminatorBranch(BasicBlock bb) {
            List<Instruction> insts = bb.getInstructions();
            for (int i = insts.size() - 1; i >= 0; i--) {
                Instruction inst = insts.get(i);
                if (inst instanceof BrInst br) {
                    return br;
                }
                if (inst instanceof ReturnInst) {
                    return null;
                }
            }
            return null;
        }

        Set<BasicBlock> succ(BasicBlock bb) {
            return successors.getOrDefault(bb, new LinkedHashSet<>());
        }

        Set<BasicBlock> pred(BasicBlock bb) {
            return predecessors.getOrDefault(bb, new LinkedHashSet<>());
        }

        void addEdge(BasicBlock from, BasicBlock to) {
            if (!successors.containsKey(from) || !predecessors.containsKey(to)) {
                return;
            }
            successors.get(from).add(to);
            predecessors.get(to).add(from);
        }

        void removeEdge(BasicBlock from, BasicBlock to) {
            if (successors.containsKey(from)) {
                successors.get(from).remove(to);
            }
            if (predecessors.containsKey(to)) {
                predecessors.get(to).remove(from);
            }
        }

        void replaceSuccessor(BasicBlock from, BasicBlock oldTo, BasicBlock newTo) {
            if (oldTo == newTo) {
                return;
            }
            removeEdge(from, oldTo);
            addEdge(from, newTo);
        }

        void replacePredecessor(BasicBlock to, BasicBlock oldFrom, BasicBlock newFrom) {
            if (oldFrom == newFrom) {
                return;
            }
            if (predecessors.containsKey(to)) {
                predecessors.get(to).remove(oldFrom);
                predecessors.get(to).add(newFrom);
            }
            if (successors.containsKey(oldFrom)) {
                successors.get(oldFrom).remove(to);
            }
            if (successors.containsKey(newFrom)) {
                successors.get(newFrom).add(to);
            }
        }

        void removeBlock(BasicBlock bb) {
            for (BasicBlock p : new ArrayList<>(pred(bb))) {
                removeEdge(p, bb);
            }
            for (BasicBlock s : new ArrayList<>(succ(bb))) {
                removeEdge(bb, s);
            }
            successors.remove(bb);
            predecessors.remove(bb);
        }
    }

    @Override
    public boolean run(Module module) {
        boolean anyChanged = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration())
                continue;
            boolean changed = true;
            while (changed) {
                LocalCfg cfg = LocalCfg.build(function);
                changed = false;
                if (removeUnreachableBlocks(function, cfg)) {
                    changed = true;
                    anyChanged = true;
                }
                if (mergeBlocks(function, cfg)) {
                    changed = true;
                    anyChanged = true;
                }
                if (removeEmptyBlocks(function, cfg)) {
                    changed = true;
                    anyChanged = true;
                }
            }
        }
        return anyChanged;
    }

    private boolean removeEmptyBlocks(Function function, LocalCfg cfg) {
        boolean changed = false;
        boolean localChanged = true;
        while (localChanged) {
            localChanged = false;
            List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
            for (BasicBlock bb : blocks) {
                if (bb == function.getBasicBlocks().get(0))
                    continue;
                if (bb.getInstructions().size() != 1)
                    continue;
                Instruction inst = bb.getInstructions().get(0);
                if (!(inst instanceof BrInst br) || br.getNumOperands() != 1)
                    continue;

                if (!(br.getOperand(0) instanceof BasicBlock target))
                    continue;
                if (target == bb)
                    continue;

                List<BasicBlock> predecessors = new ArrayList<>(cfg.pred(bb));

                if (cfg.pred(target).size() > 1) {
                    if (predecessors.size() > 1)
                        continue;
                    if (predecessors.size() == 1) {
                        BasicBlock pred = predecessors.get(0);
                        if (!pred.getInstructions().isEmpty()) {
                            Instruction lastPredInst = pred.getInstructions().get(pred.getInstructions().size() - 1);
                            if (lastPredInst instanceof BrInst predBr && predBr.getNumOperands() > 1) {
                                continue;
                            }
                        }
                    }
                }

                boolean safe = true;
                for (Instruction targetInst : target.getInstructions()) {
                    if (targetInst instanceof PhiInst phi) {
                        Value valFromBb = phi.getIncomingValue(bb);
                        if (valFromBb != null) {
                            for (BasicBlock pred : predecessors) {
                                Value valFromPred = phi.getIncomingValue(pred);
                                if (valFromPred != null && valFromPred != valFromBb) {
                                    safe = false;
                                    break;
                                }
                            }
                        }
                    }
                    if (!safe)
                        break;
                }
                if (!safe)
                    continue;

                for (BasicBlock pred : predecessors) {
                    Instruction predLast = pred.getInstructions().get(pred.getInstructions().size() - 1);
                    if (predLast instanceof BrInst predBr) {
                        for (int i = 0; i < predBr.getNumOperands(); i++) {
                            if (predBr.getOperand(i) == bb) {
                                predBr.setOperand(i, target);
                            }
                        }
                    }
                    cfg.replaceSuccessor(pred, bb, target);
                }

                for (Instruction targetInst : target.getInstructions()) {
                    if (targetInst instanceof PhiInst phi) {
                        Value val = phi.getIncomingValue(bb);
                        if (val != null) {
                            phi.removeIncoming(bb);
                            for (BasicBlock pred : predecessors) {
                                if (phi.getIncomingValue(pred) == null) {
                                    phi.setIncoming(pred, val);
                                }
                            }
                        }
                    }
                }

                inst.dropAllReferences();
                function.getBasicBlocks().remove(bb);
                cfg.removeBlock(bb);
                changed = true;
                localChanged = true;
                break;
            }
        }
        return changed;
    }

    private boolean removeUnreachableBlocks(Function function, LocalCfg cfg) {
        if (function.getBasicBlocks().isEmpty())
            return false;

        Set<BasicBlock> reachable = new LinkedHashSet<>();
        Queue<BasicBlock> queue = new LinkedList<>();

        BasicBlock entry = function.getBasicBlocks().get(0);
        reachable.add(entry);
        queue.add(entry);

        while (!queue.isEmpty()) {
            BasicBlock bb = queue.poll();
            for (BasicBlock target : cfg.succ(bb)) {
                if (reachable.add(target)) {
                    queue.add(target);
                }
            }
        }

        if (reachable.size() == function.getBasicBlocks().size())
            return false;

        List<BasicBlock> allBlocks = new ArrayList<>(function.getBasicBlocks());
        boolean changed = false;
        for (BasicBlock bb : allBlocks) {
            if (!reachable.contains(bb)) {
                // Remove this block from its successors' Phi instructions
                for (BasicBlock target : cfg.succ(bb)) {
                    removePredecessorFromPhis(target, bb);
                }
                for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                    inst.dropAllReferences();
                }
                function.getBasicBlocks().remove(bb);
                cfg.removeBlock(bb);
                changed = true;
            }
        }
        return changed;
    }

    private void removePredecessorFromPhis(BasicBlock target, BasicBlock pred) {
        for (Instruction inst : target.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                phi.removeIncoming(pred);
            }
        }
    }

    private boolean mergeBlocks(Function function, LocalCfg cfg) {
        boolean changed = false;
        boolean localChanged = true;
        while (localChanged) {
            localChanged = false;
            List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
            for (BasicBlock bb : blocks) {
                if (bb.getInstructions().isEmpty())
                    continue;
                Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
                if (!(last instanceof BrInst br) || br.getNumOperands() != 1)
                    continue;

                if (!(br.getOperand(0) instanceof BasicBlock target))
                    continue;
                if (target == bb)
                    continue;
                if (cfg.pred(target).size() != 1 || !cfg.pred(target).contains(bb))
                    continue;

                // Merge target into bb
                bb.getInstructions().remove(last);
                br.dropAllReferences();

                // Get successors before moving instructions
                List<BasicBlock> successors = getSuccessors(target);

                List<Instruction> targetInsts = new ArrayList<>(target.getInstructions());
                for (Instruction inst : targetInsts) {
                    if (inst instanceof PhiInst phi) {
                        Value val = phi.getIncomingValue(bb);
                        if (val != null) {
                            phi.replaceAllUsesWith(val);
                            phi.dropAllReferences();
                            target.getInstructions().remove(phi);
                            continue;
                        }
                    }
                    target.getInstructions().remove(inst);
                    bb.getInstructions().add(inst);
                    inst.setParent(bb);
                }

                // Update Phis in successors of target
                for (BasicBlock successor : successors) {
                    for (Instruction inst : successor.getInstructions()) {
                        if (inst instanceof PhiInst phi) {
                            Value val = phi.getIncomingValue(target);
                            if (val != null) {
                                phi.removeIncoming(target);
                                phi.setIncoming(bb, val);
                            }
                        }
                    }
                }

                target.replaceAllUsesWith(bb);
                function.getBasicBlocks().remove(target);

                cfg.removeEdge(bb, target);
                for (BasicBlock succ : new ArrayList<>(cfg.succ(target))) {
                    cfg.replacePredecessor(succ, target, bb);
                }
                cfg.removeBlock(target);

                changed = true;
                localChanged = true;
                break;
            }
        }
        return changed;
    }

    private List<BasicBlock> getSuccessors(BasicBlock bb) {
        List<BasicBlock> successors = new ArrayList<>();
        if (bb.getInstructions().isEmpty())
            return successors;
        Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
        if (last instanceof BrInst br) {
            for (int i = 0; i < br.getNumOperands(); i++) {
                if (br.getOperand(i) instanceof BasicBlock target) {
                    successors.add(target);
                }
            }
        }
        return successors;
    }

}
