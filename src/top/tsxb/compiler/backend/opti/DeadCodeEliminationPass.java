package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;

import java.util.ArrayList;
import java.util.List;

public class DeadCodeEliminationPass implements Pass {
    @Override
    public void run(Module module) {
        for (Function function : module.getFunctionList()) {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (BasicBlock bb : function.getBasicBlocks()) {
                    List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
                    for (Instruction inst : instructions) {
                        if (!inst.hasUses() && !hasSideEffects(inst)) {
                            inst.dropAllReferences();
                            bb.getInstructions().remove(inst);
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    private boolean hasSideEffects(Instruction inst) {
        return inst instanceof StoreInst ||
               inst instanceof CallInst ||
               inst instanceof BrInst ||
               inst instanceof ReturnInst;
    }
}
