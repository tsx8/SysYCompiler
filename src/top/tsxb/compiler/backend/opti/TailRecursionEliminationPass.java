package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.List;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.AllocaInst;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.CallInst;
import top.tsxb.compiler.ir.inst.GetElementPtrInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.inst.ReturnInst;
import top.tsxb.compiler.ir.inst.StoreInst;
import top.tsxb.compiler.ir.structure.Argument;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.NoneType;

public class TailRecursionEliminationPass implements Pass {
    @Override
    public boolean run(Module module) {
        boolean anyChanged = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration())
                continue;
            if (processFunction(function)) {
                anyChanged = true;
            }
        }
        return anyChanged;
    }

    private boolean processFunction(Function function) {
        List<CallInst> tailCalls = findTailCalls(function);
        if (tailCalls.isEmpty()) {
            return false;
        }

        List<CallInst> allRecursiveCalls = findAllRecursiveCalls(function);
        if (allRecursiveCalls.size() != tailCalls.size()) {
            return false;
        }

        if (modifiesGlobals(function)) {
            return false;
        }

        BasicBlock oldEntry = function.getBasicBlocks().get(0);
        BasicBlock treHeader = new BasicBlock("tre_header", function);
        BasicBlock oldEntryBody = new BasicBlock("old_entry_body", function);

        function.getBasicBlocks().remove(treHeader);
        function.getBasicBlocks().remove(oldEntryBody);
        function.getBasicBlocks().add(1, treHeader);
        function.getBasicBlocks().add(2, oldEntryBody);

        List<PhiInst> phis = new ArrayList<>();
        for (Argument arg : function.getArguments()) {
            PhiInst phi = new PhiInst(arg.getType(), arg.getName() + "_phi", treHeader);
            arg.replaceAllUsesWith(phi);
            phi.setIncoming(oldEntry, arg);
            phis.add(phi);
        }
        treHeader.addInstruction(new BrInst(oldEntryBody, treHeader));

        List<Instruction> oldEntryInsts = new ArrayList<>(oldEntry.getInstructions());
        oldEntry.getInstructions().clear();
        for (Instruction inst : oldEntryInsts) {
            if (inst instanceof AllocaInst) {
                oldEntry.getInstructions().add(inst);
                inst.setParent(oldEntry);
            } else {
                oldEntryBody.getInstructions().add(inst);
                inst.setParent(oldEntryBody);
            }
        }
        oldEntry.addInstruction(new BrInst(treHeader, oldEntry));

        for (CallInst call : tailCalls) {
            BasicBlock bb = call.getParent();
            for (int i = 0; i < phis.size(); i++) {
                phis.get(i).setIncoming(bb, call.getOperand(i + 1));
            }

            List<Instruction> insts = bb.getInstructions();
            int callIdx = insts.indexOf(call);

            BrInst br = new BrInst(treHeader, null);
            insts.set(callIdx, br);
            br.setParent(bb);

            if (callIdx + 1 < insts.size() && insts.get(callIdx + 1) instanceof ReturnInst) {
                Instruction ret = insts.remove(callIdx + 1);
                ret.dropAllReferences();
            }

            call.dropAllReferences();
        }

        return true;
    }

    private List<CallInst> findTailCalls(Function function) {
        List<CallInst> tailCalls = new ArrayList<>();
        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = bb.getInstructions();
            if (insts.isEmpty())
                continue;
            Instruction last = insts.get(insts.size() - 1);
            if (last instanceof ReturnInst ret) {
                if (insts.size() >= 2) {
                    Instruction prev = insts.get(insts.size() - 2);
                    if (prev instanceof CallInst call && call.getOperand(0) == function) {
                        if (function.getValueType() instanceof FuncType funcType) {
                            if (funcType.getReturnType() instanceof NoneType
                                || (ret.getNumOperands() > 0 && ret.getOperand(0) == call)) {
                                tailCalls.add(call);
                            }
                        }
                    }
                }
            }
        }
        return tailCalls;
    }

    private List<CallInst> findAllRecursiveCalls(Function function) {
        List<CallInst> recursiveCalls = new ArrayList<>();
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst call && call.getOperand(0) == function) {
                    recursiveCalls.add(call);
                }
            }
        }
        return recursiveCalls;
    }

    private boolean modifiesGlobals(Function function) {
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof StoreInst store) {
                    Value ptr = store.getOperand(1);
                    if (isGlobalDerived(ptr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isGlobalDerived(Value ptr) {
        if (ptr instanceof GlobalVariable) {
            return true;
        }
        if (ptr instanceof GetElementPtrInst gep) {
            return isGlobalDerived(gep.getOperand(0));
        }
        return false;
    }
}
