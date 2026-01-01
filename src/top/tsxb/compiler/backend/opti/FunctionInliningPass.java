package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.NoneType;

import java.util.*;

public class FunctionInliningPass implements Pass {
    private static final int MAX_INLINE_SIZE = 40;
    private static final int MAX_RECURSIVE_INLINE_SIZE = 50;

    @Override
    public boolean run(Module module) {
        boolean changed = false;
        Set<Function> recursiveFunctions = findRecursiveFunctions(module);
        List<Function> functions = new ArrayList<>(module.getFunctionList());
        for (Function caller : functions) {
            if (caller.isDeclaration()) continue;
            if (inlineInFunction(caller, recursiveFunctions)) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean inlineInFunction(Function caller, Set<Function> recursiveFunctions) {
        boolean changed = false;
        boolean localChanged = true;
        while (localChanged) {
            localChanged = false;
            List<BasicBlock> blocks = new ArrayList<>(caller.getBasicBlocks());
            for (BasicBlock bb : blocks) {
                List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
                for (Instruction inst : instructions) {
                    if (inst instanceof CallInst call) {
                        Function callee = (Function) call.getOperand(0);
                        if (shouldInline(call, callee, recursiveFunctions)) {
                            inlineCall(caller, bb, call, callee);
                            changed = true;
                            localChanged = true;
                            break; 
                        }
                    }
                }
                if (localChanged) break;
            }
        }
        return changed;
    }

    private boolean shouldInline(CallInst call, Function callee, Set<Function> recursiveFunctions) {
        if (callee.isDeclaration()) return false;
        if (callee.getName().equals("main")) return false;

        boolean recursive = recursiveFunctions.contains(callee);
        boolean allConst = true;
        for (int i = 1; i < call.getNumOperands(); i++) {
            if (!(call.getOperand(i) instanceof ConstInt)) {
                allConst = false;
                break;
            }
        }

        int instCount = 0;
        for (BasicBlock bb : callee.getBasicBlocks()) {
            instCount += bb.getInstructions().size();
        }

        if (recursive) {
            return allConst && instCount < MAX_RECURSIVE_INLINE_SIZE;
        }

        return instCount < MAX_INLINE_SIZE;
    }

    private Set<Function> findRecursiveFunctions(Module module) {
        Set<Function> recursiveFunctions = new HashSet<>();
        Map<Function, Set<Function>> callGraph = new HashMap<>();

        for (Function f : module.getFunctionList()) {
            if (f.isDeclaration()) continue;
            Set<Function> callees = new HashSet<>();
            for (BasicBlock bb : f.getBasicBlocks()) {
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof CallInst call) {
                        callees.add((Function) call.getOperand(0));
                    }
                }
            }
            callGraph.put(f, callees);
        }

        for (Function f : callGraph.keySet()) {
            if (hasPath(f, f, callGraph, new HashSet<>())) {
                recursiveFunctions.add(f);
            }
        }
        return recursiveFunctions;
    }

    private boolean hasPath(Function start, Function target, Map<Function, Set<Function>> graph, Set<Function> visited) {
        Set<Function> callees = graph.get(start);
        if (callees == null) return false;
        for (Function callee : callees) {
            if (callee == target) return true;
            if (visited.add(callee)) {
                if (hasPath(callee, target, graph, visited)) return true;
            }
        }
        return false;
    }

    private void inlineCall(Function caller, BasicBlock bb, CallInst call, Function callee) {
        List<Instruction> bbInsts = bb.getInstructions();
        int callIdx = bbInsts.indexOf(call);
        
        BasicBlock afterBlock = new BasicBlock(bb.getName() + ".inline.after", caller);
        List<Instruction> afterInsts = new ArrayList<>(bbInsts.subList(callIdx + 1, bbInsts.size()));
        bbInsts.subList(callIdx, bbInsts.size()).clear();
        
        for (Instruction inst : afterInsts) {
            afterBlock.addInstruction(inst);
        }

        Map<Value, Value> valueMap = new HashMap<>();
        for (int i = 0; i < callee.getArguments().size(); i++) {
            valueMap.put(callee.getArguments().get(i), call.getOperand(i + 1));
        }

        List<BasicBlock> clonedBlocks = new ArrayList<>();
        for (BasicBlock calleeBB : callee.getBasicBlocks()) {
            BasicBlock clonedBB = new BasicBlock(calleeBB.getName() + ".inline", caller);
            valueMap.put(calleeBB, clonedBB);
            clonedBlocks.add(clonedBB);
        }

        List<ReturnInst> returnInsts = new ArrayList<>();
        BasicBlock callerEntry = caller.getBasicBlocks().get(0);
        for (int i = 0; i < callee.getBasicBlocks().size(); i++) {
            BasicBlock calleeBB = callee.getBasicBlocks().get(i);
            BasicBlock clonedBB = clonedBlocks.get(i);
            for (Instruction inst : calleeBB.getInstructions()) {
                if (inst instanceof AllocaInst alloca) {
                    AllocaInst clonedAlloca = new AllocaInst(alloca.getAllocatedType(), alloca.getName(), null);
                    callerEntry.addFirst(clonedAlloca);
                    valueMap.put(alloca, clonedAlloca);
                } else {
                    Instruction clonedInst = cloneInstruction(inst, valueMap, clonedBB);
                    if (clonedInst instanceof ReturnInst ret) {
                        returnInsts.add(ret);
                    } else {
                        valueMap.put(inst, clonedInst);
                    }
                }
            }
        }

        for (int i = 0; i < callee.getBasicBlocks().size(); i++) {
            BasicBlock calleeBB = callee.getBasicBlocks().get(i);
            BasicBlock clonedBB = clonedBlocks.get(i);
            for (int j = 0, clonedIdx = 0; j < calleeBB.getInstructions().size(); j++) {
                Instruction inst = calleeBB.getInstructions().get(j);
                if (inst instanceof AllocaInst) continue;
                Instruction clonedInst = clonedBB.getInstructions().get(clonedIdx++);
                if (inst instanceof PhiInst phi) {
                    PhiInst clonedPhi = (PhiInst) clonedInst;
                    for (Map.Entry<BasicBlock, Value> entry : phi.getIncoming().entrySet()) {
                        clonedPhi.setIncoming((BasicBlock) valueMap.get(entry.getKey()), map(entry.getValue(), valueMap));
                    }
                }
            }
        }

        new BrInst(clonedBlocks.get(0), bb);

        // Update Phis in original successors of bb to refer to afterBlock
        for (BasicBlock successor : getSuccessors(afterBlock)) {
            for (Instruction inst : successor.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    Value val = phi.getIncomingValue(bb);
                    if (val != null) {
                        phi.removeIncoming(bb);
                        phi.setIncoming(afterBlock, val);
                    }
                }
            }
        }

        if (((FuncType) callee.getValueType()).getReturnType() instanceof NoneType) {
            for (ReturnInst ret : returnInsts) {
                BasicBlock retBB = ret.getParent();
                retBB.getInstructions().remove(ret);
                new BrInst(afterBlock, retBB);
            }
            call.replaceAllUsesWith(null);
        } else {
            PhiInst resPhi = new PhiInst(((FuncType) callee.getValueType()).getReturnType(), "inline.res", afterBlock);
            afterBlock.getInstructions().remove(resPhi);
            afterBlock.getInstructions().add(0, resPhi);
            
            for (ReturnInst ret : returnInsts) {
                BasicBlock retBB = ret.getParent();
                Value retVal = map(ret.getOperand(0), valueMap);
                resPhi.setIncoming(retBB, retVal);
                retBB.getInstructions().remove(ret);
                new BrInst(afterBlock, retBB);
            }
            call.replaceAllUsesWith(resPhi);
        }
        
        call.dropAllReferences();
    }

    private Instruction cloneInstruction(Instruction inst, Map<Value, Value> valueMap, BasicBlock newParent) {
        if (inst instanceof BinaryInst binary) {
            return new BinaryInst(binary.getOpCode(), map(binary.getOperand(0), valueMap), map(binary.getOperand(1), valueMap), newParent);
        } else if (inst instanceof IcmpInst icmp) {
            return new IcmpInst(icmp.getPredicate(), map(icmp.getOperand(0), valueMap), map(icmp.getOperand(1), valueMap), newParent);
        } else if (inst instanceof BrInst br) {
            if (br.getNumOperands() == 1) {
                return new BrInst((BasicBlock) map(br.getOperand(0), valueMap), newParent);
            } else {
                return new BrInst(map(br.getOperand(0), valueMap), (BasicBlock) map(br.getOperand(1), valueMap), (BasicBlock) map(br.getOperand(2), valueMap), newParent);
            }
        } else if (inst instanceof CallInst call) {
            Function func = (Function) call.getOperand(0);
            List<Value> args = new ArrayList<>();
            for (int i = 1; i < call.getNumOperands(); i++) {
                args.add(map(call.getOperand(i), valueMap));
            }
            return new CallInst(func, args, newParent);
        } else if (inst instanceof ReturnInst ret) {
            if (ret.getNumOperands() == 0) {
                return new ReturnInst(newParent);
            } else {
                return new ReturnInst(map(ret.getOperand(0), valueMap), newParent);
            }
        } else if (inst instanceof LoadInst load) {
            return new LoadInst(map(load.getOperand(0), valueMap), newParent);
        } else if (inst instanceof StoreInst store) {
            return new StoreInst(map(store.getOperand(0), valueMap), map(store.getOperand(1), valueMap), newParent);
        } else if (inst instanceof AllocaInst alloca) {
            return new AllocaInst(alloca.getAllocatedType(), alloca.getName(), newParent);
        } else if (inst instanceof GetElementPtrInst gep) {
            List<Value> indices = new ArrayList<>();
            for (int i = 1; i < gep.getNumOperands(); i++) {
                indices.add(map(gep.getOperand(i), valueMap));
            }
            return new GetElementPtrInst(map(gep.getOperand(0), valueMap), indices, newParent);
        } else if (inst instanceof ZextInst zext) {
            return new ZextInst(map(zext.getOperand(0), valueMap), zext.getType(), newParent);
        } else if (inst instanceof PhiInst phi) {
            return new PhiInst(phi.getType(), phi.getName(), newParent);
        }
        return null;
    }

    private Value map(Value val, Map<Value, Value> valueMap) {
        if (valueMap.containsKey(val)) {
            return valueMap.get(val);
        }
        return val;
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
}
