package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.*;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.backend.mips.LoopAnalysis;

import java.util.*;

public class LicmPass implements Pass {
    private DominatorAnalysis.DominatorInfo domInfo;
    private LoopAnalysis loopAnalysis;

    @Override
    public boolean run(Module module) {
        boolean changed = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration()) continue;
            changed |= runOnFunction(function);
        }
        return changed;
    }

    private boolean runOnFunction(Function function) {
        boolean changed = false;
        boolean localChanged = true;
        while (localChanged) {
            localChanged = false;
            domInfo = DominatorAnalysis.computeDominators(function);
            loopAnalysis = new LoopAnalysis(function);
            loopAnalysis.analyze();

            List<Loop> loops = findLoops(function);
            // Sort loops by depth (deepest first) to hoist out of nested loops step by step
            loops.sort((l1, l2) -> Integer.compare(loopAnalysis.getLoopDepth(l2.header), loopAnalysis.getLoopDepth(l1.header)));

            for (Loop loop : loops) {
                localChanged |= hoistInvariants(loop, function);
            }
            changed |= localChanged;
        }
        return changed;
    }

    private record Loop(BasicBlock header, Set<BasicBlock> blocks) {}

    private List<Loop> findLoops(Function function) {
        List<Loop> loops = new ArrayList<>();
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        for (BasicBlock n : function.getBasicBlocks()) {
            if (!domInfo.dominators().containsKey(n)) continue;
            for (BasicBlock d : cfg.successors().getOrDefault(n, List.of())) {
                if (domInfo.dominators().get(n).contains(d)) {
                    Set<BasicBlock> loopBlocks = DominatorAnalysis.findLoopBlocks(n, d, cfg.predecessors());
                    loops.add(new Loop(d, loopBlocks));
                }
            }
        }
        return loops;
    }

    private boolean hoistInvariants(Loop loop, Function function) {
        BasicBlock preHeader = getOrCreatePreHeader(loop, function);
        if (preHeader == null) return false;

        Set<Instruction> invariants = new LinkedHashSet<>();
        boolean changed = false;
        boolean found;
        do {
            found = false;
            for (BasicBlock bb : loop.blocks) {
                for (Instruction inst : bb.getInstructions()) {
                    if (invariants.contains(inst)) continue;
                    if (isInvariant(inst, loop, invariants)) {
                        invariants.add(inst);
                        found = true;
                        changed = true;
                    }
                }
            }
        } while (found);

        if (invariants.isEmpty()) return changed;

        // Move invariants to pre-header
        List<Instruction> preHeaderInsts = preHeader.getInstructions();
        int insertPos = Math.max(0, preHeaderInsts.size() - 1);
        for (Instruction inst : invariants) {
            inst.getParent().getInstructions().remove(inst);
            preHeaderInsts.add(insertPos++, inst);
            inst.setParent(preHeader);
        }

        return changed;
    }

    private BasicBlock getOrCreatePreHeader(Loop loop, Function function) {
        List<BasicBlock> preds = new ArrayList<>();
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        for (BasicBlock pred : cfg.predecessors().getOrDefault(loop.header, List.of())) {
            if (!loop.blocks.contains(pred)) {
                preds.add(pred);
            }
        }

        if (preds.size() == 1) {
            return preds.get(0);
        }
        return null;
    }

    private boolean isInvariant(Instruction inst, Loop loop, Set<Instruction> invariants) {
        if (inst instanceof PhiInst || inst instanceof BrInst || inst instanceof ReturnInst || inst instanceof StoreInst || inst instanceof AllocaInst) {
            return false;
        }

        if (inst instanceof CallInst) {
            return false;
        }

        for (int i = 0; i < inst.getNumOperands(); i++) {
            Value op = inst.getOperand(i);
            if (!isValueInvariant(op, loop, invariants)) {
                return false;
            }
        }

        if (inst instanceof LoadInst load) {
            return isLoadInvariant(load, loop, invariants);
        }

        // For instructions that might trap (SDIV, SREM), we should only hoist if they are guaranteed to execute.
        if (inst.getOpCode() == OpCode.SDIV || inst.getOpCode() == OpCode.SREM) {
            return dominatesAllExits(inst.getParent(), loop);
        }

        return true;
    }

    private boolean isValueInvariant(Value val, Loop loop, Set<Instruction> invariants) {
        if (val instanceof Instruction inst) {
            return !loop.blocks.contains(inst.getParent()) || invariants.contains(inst);
        }
        return true; // Constants, GlobalVariables, Arguments are invariant
    }

    private boolean isLoadInvariant(LoadInst load, Loop loop, Set<Instruction> invariants) {
        Value ptr = load.getOperand(0);
        // Pointer must be invariant
        if (!isValueInvariant(ptr, loop, invariants)) {
            return false;
        }

        // Check if any store in the loop might alias with this load
        for (BasicBlock bb : loop.blocks) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof StoreInst store) {
                    if (mayAlias(ptr, store.getOperand(1))) {
                        return false;
                    }
                }
                if (inst instanceof CallInst call) {
                    Value callee = call.getOperand(0);
                    if (!(callee instanceof Function func) || !isReadOnly(func)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isReadOnly(Function func) {
        String name = func.getName();
        return name.equals("putint") || name.equals("putch") || name.equals("putf") ||
               name.equals("starttime") || name.equals("stoptime") || name.equals("putarray") ||
               name.equals("getint") || name.equals("getch");
    }

    private boolean mayAlias(Value p1, Value p2) {
        if (p1 == p2) return true;
        
        Value b1 = getBase(p1);
        Value b2 = getBase(p2);
        
        if (b1 != b2) {
            if ((b1 instanceof AllocaInst || b1 instanceof GlobalVariable) &&
                (b2 instanceof AllocaInst || b2 instanceof GlobalVariable)) {
                return false;
            }
            return (!(b1 instanceof Argument) || !(b2 instanceof AllocaInst)) &&
                   (!(b2 instanceof Argument) || !(b1 instanceof AllocaInst));
        }
        
        if (p1 instanceof GetElementPtrInst gep1 && p2 instanceof GetElementPtrInst gep2) {
            if (gep1.getNumOperands() == gep2.getNumOperands()) {
                boolean allConst = true;
                boolean allEqual = true;
                for (int i = 1; i < gep1.getNumOperands(); i++) {
                    Value idx1 = gep1.getOperand(i);
                    Value idx2 = gep2.getOperand(i);
                    if (idx1 instanceof top.tsxb.compiler.ir.constant.ConstInt c1 && 
                        idx2 instanceof top.tsxb.compiler.ir.constant.ConstInt c2) {
                        if (c1.getValue() != c2.getValue()) {
                            allEqual = false;
                        }
                    } else {
                        allConst = false;
                        break;
                    }
                }
                return !allConst || allEqual;
            }
        }
        
        return true;
    }

    private Value getBase(Value v) {
        if (v instanceof GetElementPtrInst gep) {
            return getBase(gep.getOperand(0));
        }
        return v;
    }

    private boolean dominatesAllExits(BasicBlock bb, Loop loop) {
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(bb.getParent());
        for (BasicBlock loopBlock : loop.blocks) {
            for (BasicBlock succ : cfg.successors().getOrDefault(loopBlock, List.of())) {
                if (!loop.blocks.contains(succ)) {
                    if (!domInfo.dominates(bb, loopBlock)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
