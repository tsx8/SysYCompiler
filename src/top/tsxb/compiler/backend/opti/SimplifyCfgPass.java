package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;

public class SimplifyCfgPass implements Pass {
    @Override
    public boolean run(Module module) {
        boolean anyChanged = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration())
                continue;
            boolean changed = true;
            while (changed) {
                changed = false;
                if (removeUnreachableBlocks(function)) {
                    changed = true;
                    anyChanged = true;
                }
                if (mergeBlocks(function)) {
                    changed = true;
                    anyChanged = true;
                }
                if (removeEmptyBlocks(function)) {
                    changed = true;
                    anyChanged = true;
                }
            }
        }
        return anyChanged;
    }

    private boolean removeEmptyBlocks(Function function) {
        boolean changed = false;
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
        for (BasicBlock bb : blocks) {
            if (bb == function.getBasicBlocks().get(0))
                continue;
            if (bb.getInstructions().size() != 1)
                continue;
            Instruction inst = bb.getInstructions().get(0);
            if (inst instanceof BrInst br && br.getNumOperands() == 1) {
                BasicBlock target = (BasicBlock)br.getOperand(0);
                if (target == bb)
                    continue;

                List<BasicBlock> predecessors = getPredecessors(bb, function);

                if (getPredecessorCount(target, function) > 1) {
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
                changed = true;
            }
        }
        return changed;
    }

    private List<BasicBlock> getPredecessors(BasicBlock target, Function function) {
        List<BasicBlock> preds = new ArrayList<>();
        for (BasicBlock bb : function.getBasicBlocks()) {
            if (bb.getInstructions().isEmpty())
                continue;
            Instruction inst = bb.getInstructions().get(bb.getInstructions().size() - 1);
            if (inst instanceof BrInst br) {
                for (int i = 0; i < br.getNumOperands(); i++) {
                    if (br.getOperand(i) == target) {
                        preds.add(bb);
                        break;
                    }
                }
            }
        }
        return preds;
    }

    private boolean removeUnreachableBlocks(Function function) {
        if (function.getBasicBlocks().isEmpty())
            return false;

        Set<BasicBlock> reachable = new LinkedHashSet<>();
        Queue<BasicBlock> queue = new LinkedList<>();

        BasicBlock entry = function.getBasicBlocks().get(0);
        reachable.add(entry);
        queue.add(entry);

        while (!queue.isEmpty()) {
            BasicBlock bb = queue.poll();
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof BrInst br) {
                    for (int i = 0; i < br.getNumOperands(); i++) {
                        if (br.getOperand(i) instanceof BasicBlock target) {
                            if (reachable.add(target)) {
                                queue.add(target);
                            }
                        }
                    }
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
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof BrInst br) {
                        for (int i = 0; i < br.getNumOperands(); i++) {
                            if (br.getOperand(i) instanceof BasicBlock target) {
                                removePredecessorFromPhis(target, bb);
                            }
                        }
                    }
                }
                bb.replaceAllUsesWith(null);
                for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                    inst.dropAllReferences();
                }
                function.getBasicBlocks().remove(bb);
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

    private boolean mergeBlocks(Function function) {
        boolean changed = false;
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
        for (int i = 0; i < blocks.size(); i++) {
            BasicBlock bb = blocks.get(i);
            if (bb.getInstructions().isEmpty())
                continue;
            Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
            if (last instanceof BrInst br && br.getNumOperands() == 1) {
                BasicBlock target = (BasicBlock)br.getOperand(0);
                if (target != bb && getPredecessorCount(target, function) == 1) {
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
                    changed = true;
                    // Re-fetch blocks since we modified the list
                    blocks = new ArrayList<>(function.getBasicBlocks());
                    i--; // Re-check current block
                }
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

    private int getPredecessorCount(BasicBlock target, Function function) {
        int count = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            if (bb.getInstructions().isEmpty())
                continue;
            Instruction inst = bb.getInstructions().get(bb.getInstructions().size() - 1);
            if (inst instanceof BrInst br) {
                for (int i = 0; i < br.getNumOperands(); i++) {
                    if (br.getOperand(i) == target) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
