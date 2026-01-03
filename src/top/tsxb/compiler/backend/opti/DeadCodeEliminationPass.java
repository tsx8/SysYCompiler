package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;

import java.util.*;

public class DeadCodeEliminationPass implements Pass {
    @Override
    public boolean run(Module module) {
        boolean anyChanged = false;
        for (Function function : module.getFunctionList()) {
            if (runOnFunction(function)) {
                anyChanged = true;
            }
        }
        return anyChanged;
    }

    private boolean runOnFunction(Function function) {
        boolean changed = runDSE(function);

        Set<Instruction> marked = new HashSet<>();
        Queue<Instruction> worklist = new LinkedList<>();

        // 1. Mark critical instructions
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (isCritical(inst)) {
                    marked.add(inst);
                    worklist.add(inst);
                }
            }
        }

        // 2. Propagate
        while (!worklist.isEmpty()) {
            Instruction inst = worklist.poll();
            for (int i = 0; i < inst.getNumOperands(); i++) {
                Value operand = inst.getOperand(i);
                if (operand instanceof Instruction opInst) {
                    if (!marked.contains(opInst)) {
                        marked.add(opInst);
                        worklist.add(opInst);
                    }
                }
            }
        }

        // 3. Sweep
        for (BasicBlock bb : function.getBasicBlocks()) {
            Iterator<Instruction> it = bb.getInstructions().iterator();
            while (it.hasNext()) {
                Instruction inst = it.next();
                if (!marked.contains(inst)) {
                    inst.dropAllReferences();
                    it.remove();
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean runDSE(Function function) {
        boolean changed = false;
        for (BasicBlock bb : function.getBasicBlocks()) {
            Set<Value> killed = new HashSet<>();
            List<Instruction> instructions = bb.getInstructions();
            ListIterator<Instruction> it = instructions.listIterator(instructions.size());
            while (it.hasPrevious()) {
                Instruction inst = it.previous();
                if (inst instanceof StoreInst) {
                    Value ptr = inst.getOperand(1);
                    if (killed.contains(ptr)) {
                        inst.dropAllReferences();
                        it.remove();
                        changed = true;
                        continue;
                    }
                    killed.add(ptr);
                } else if (inst instanceof LoadInst) {
                    Value ptr = inst.getOperand(0);
                    killed.removeIf(k -> mayAlias(k, ptr));
                } else if (inst instanceof CallInst) {
                    killed.clear();
                }
            }
        }
        return changed;
    }

    private boolean mayAlias(Value v1, Value v2) {
        if (v1 == v2) return true;
        Value base1 = getUnderlyingObject(v1);
        Value base2 = getUnderlyingObject(v2);
        return base1 == base2;
    }

    private Value getUnderlyingObject(Value ptr) {
        if (ptr instanceof GetElementPtrInst gep) {
            return getUnderlyingObject(gep.getOperand(0));
        }
        return ptr;
    }

    private boolean isCritical(Instruction inst) {
        return inst instanceof StoreInst ||
               inst instanceof CallInst ||
               inst instanceof BrInst ||
               inst instanceof ReturnInst;
    }
}
