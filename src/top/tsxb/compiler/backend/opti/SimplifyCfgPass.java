package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;

import java.util.*;

public class SimplifyCfgPass implements Pass {
    @Override
    public boolean run(Module module) {
        boolean anyChanged = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration()) continue;
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
            }
        }
        return anyChanged;
    }

    private boolean removeUnreachableBlocks(Function function) {
        if (function.getBasicBlocks().isEmpty()) return false;
        
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
        
        if (reachable.size() == function.getBasicBlocks().size()) return false;
        
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
            if (bb.getInstructions().isEmpty()) continue;
            Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
            if (last instanceof BrInst br && br.getNumOperands() == 1) {
                BasicBlock target = (BasicBlock) br.getOperand(0);
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
                        bb.addInstruction(inst);
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
        if (bb.getInstructions().isEmpty()) return successors;
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
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof BrInst br) {
                    for (int i = 0; i < br.getNumOperands(); i++) {
                        if (br.getOperand(i) == target) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }
}
