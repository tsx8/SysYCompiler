package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.ArrType;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.NoneType;

public class FunctionInliningPass implements Pass {
    private static final int MAX_INLINE_SIZE = 30;
    private static final int MAX_INLINE_SIZE_IN_LOOP = 16;
    private static final int MAX_RECURSIVE_INLINE_SIZE = 64;
    private static final int MAX_INLINE_ALLOCA_BYTES = 512;
    private static final int MAX_CALLER_SIZE_AFTER_INLINE = 4096;
    private static final int MAX_INLINED_CALLS_PER_FUNCTION = 256;

    @Override
    public boolean run(Module module) {
        boolean changed = false;
        Set<Function> recursiveFunctions = findRecursiveFunctions(module);
        List<Function> functions = new ArrayList<>(module.getFunctionList());
        for (Function caller : functions) {
            if (caller.isDeclaration())
                continue;
            if (inlineInFunction(caller, recursiveFunctions)) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean inlineInFunction(Function caller, Set<Function> recursiveFunctions) {
        boolean changed = false;
        boolean localChanged = true;
        int inlinedCalls = 0;
        while (localChanged) {
            localChanged = false;
            if (inlinedCalls >= MAX_INLINED_CALLS_PER_FUNCTION) {
                break;
            }
            int callerInstCount = countInstructions(caller);
            if (callerInstCount >= MAX_CALLER_SIZE_AFTER_INLINE) {
                break;
            }
            Set<BasicBlock> callerLoopBlocks = computeLoopBlocks(caller);
            List<BasicBlock> blocks = new ArrayList<>(caller.getBasicBlocks());
            for (BasicBlock bb : blocks) {
                List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
                for (Instruction inst : instructions) {
                    if (inst instanceof CallInst call) {
                        Function callee = (Function)call.getOperand(0);
                        boolean inLoop = callerLoopBlocks.contains(bb);
                        if (shouldInline(call, callerInstCount, callee, recursiveFunctions, inLoop)) {
                            inlineCall(caller, bb, call, callee);
                            changed = true;
                            localChanged = true;
                            inlinedCalls++;
                            break;
                        }
                    }
                }
                if (localChanged)
                    break;
            }
        }
        return changed;
    }

    private boolean shouldInline(CallInst call, int callerInstCount, Function callee, Set<Function> recursiveFunctions,
        boolean callsiteInLoop) {
        if (callee.isDeclaration())
            return false;
        if (callee.getName().equals("main"))
            return false;

        int calleeInstCount = countInstructions(callee);

        boolean recursive = recursiveFunctions.contains(callee);
        if (recursive) {
            // Only inline recursive calls for compile-time specialization (all-const args), and avoid cases like fib()
            // where multiple recursive calls can quickly cause exponential IR growth.
            if (!allArgsConstInt(call)) {
                return false;
            }
            if (countCallsTo(recursiveFunctions, callee) > 1) {
                return false;
            }
            if (calleeInstCount >= MAX_RECURSIVE_INLINE_SIZE) {
                return false;
            }
        } else {
            // Avoid inlining loop-containing callees into loop nests; this tends to create spill-heavy hot paths.
            if (callsiteInLoop && hasLoop(callee)) {
                return false;
            }

            int maxInlineSize = callsiteInLoop ? MAX_INLINE_SIZE_IN_LOOP : MAX_INLINE_SIZE;
            if (calleeInstCount >= maxInlineSize) {
                return false;
            }
        }

        // Avoid inlining stack-heavy callees, as inlining duplicates their frame into the caller.
        if (estimateAllocaBytes(callee) > MAX_INLINE_ALLOCA_BYTES) {
            return false;
        }

        // Keep the caller from growing too large; large IR tends to spill badly in both LLVM and our MIPS backend.
        return callerInstCount + calleeInstCount < MAX_CALLER_SIZE_AFTER_INLINE;
    }

    private boolean allArgsConstInt(CallInst call) {
        if (call.getNumOperands() <= 1) {
            return false;
        }
        for (int i = 1; i < call.getNumOperands(); i++) {
            if (!(call.getOperand(i) instanceof ConstInt)) {
                return false;
            }
        }
        return true;
    }

    private int countCallsTo(Set<Function> targetFunctions, Function function) {
        int count = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst call) {
                    Function callee = (Function)call.getOperand(0);
                    if (targetFunctions.contains(callee)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countInstructions(Function function) {
        int count = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            count += bb.getInstructions().size();
        }
        return count;
    }

    private boolean hasLoop(Function function) {
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);
        for (BasicBlock n : function.getBasicBlocks()) {
            if (!domInfo.dominators().containsKey(n)) {
                continue;
            }
            for (BasicBlock succ : cfg.successors().getOrDefault(n, List.of())) {
                if (domInfo.dominators().get(n).contains(succ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<BasicBlock> computeLoopBlocks(Function function) {
        Set<BasicBlock> loopBlocks = new LinkedHashSet<>();
        if (function.getBasicBlocks().isEmpty()) {
            return loopBlocks;
        }
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);
        for (BasicBlock n : function.getBasicBlocks()) {
            if (!domInfo.dominators().containsKey(n)) {
                continue;
            }
            for (BasicBlock succ : cfg.successors().getOrDefault(n, List.of())) {
                if (domInfo.dominators().get(n).contains(succ)) {
                    loopBlocks.addAll(DominatorAnalysis.findLoopBlocks(n, succ, cfg.predecessors()));
                }
            }
        }
        return loopBlocks;
    }

    private int estimateAllocaBytes(Function function) {
        int bytes = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca) {
                    bytes += sizeOf(alloca.getAllocatedType());
                    if (bytes > MAX_INLINE_ALLOCA_BYTES) {
                        return bytes;
                    }
                }
            }
        }
        return bytes;
    }

    private int sizeOf(IrType type) {
        if (type instanceof IntType intType) {
            return Math.max(1, (intType.getBitWidth() + 7) / 8);
        }
        if (type instanceof ArrType arr) {
            long total = (long)arr.getNumElements() * sizeOf(arr.getElementType());
            if (total > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int)total;
        }
        return 4;
    }

    private Set<Function> findRecursiveFunctions(Module module) {
        Map<Function, Set<Function>> callGraph = new LinkedHashMap<>();
        for (Function f : module.getFunctionList()) {
            if (f.isDeclaration())
                continue;
            Set<Function> callees = new LinkedHashSet<>();
            for (BasicBlock bb : f.getBasicBlocks()) {
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof CallInst call) {
                        Function callee = (Function)call.getOperand(0);
                        if (!callee.isDeclaration()) {
                            callees.add(callee);
                        }
                    }
                }
            }
            callGraph.put(f, callees);
        }

        return computeRecursiveFunctionsByScc(callGraph);
    }

    private Set<Function> computeRecursiveFunctionsByScc(Map<Function, Set<Function>> callGraph) {
        Set<Function> recursiveFunctions = new LinkedHashSet<>();

        Map<Function, Integer> index = new IdentityHashMap<>();
        Map<Function, Integer> lowlink = new IdentityHashMap<>();
        Deque<Function> stack = new ArrayDeque<>();
        Set<Function> onStack = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] nextIndex = new int[] {0};

        for (Function f : callGraph.keySet()) {
            if (!index.containsKey(f)) {
                strongConnect(f, callGraph, index, lowlink, stack, onStack, nextIndex, recursiveFunctions);
            }
        }

        return recursiveFunctions;
    }

    private void strongConnect(Function v, Map<Function, Set<Function>> callGraph, Map<Function, Integer> index,
        Map<Function, Integer> lowlink, Deque<Function> stack, Set<Function> onStack, int[] nextIndex,
        Set<Function> recursiveFunctions) {
        index.put(v, nextIndex[0]);
        lowlink.put(v, nextIndex[0]);
        nextIndex[0]++;
        stack.push(v);
        onStack.add(v);

        for (Function w : callGraph.getOrDefault(v, Set.of())) {
            if (!index.containsKey(w)) {
                strongConnect(w, callGraph, index, lowlink, stack, onStack, nextIndex, recursiveFunctions);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            List<Function> scc = new ArrayList<>();
            Function w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (w != v);

            if (scc.size() > 1) {
                recursiveFunctions.addAll(scc);
            } else {
                Function single = scc.get(0);
                if (callGraph.getOrDefault(single, Set.of()).contains(single)) {
                    recursiveFunctions.add(single);
                }
            }
        }
    }

    private void inlineCall(Function caller, BasicBlock bb, CallInst call, Function callee) {
        List<Instruction> bbInsts = bb.getInstructions();
        int callIdx = bbInsts.indexOf(call);

        BasicBlock afterBlock = new BasicBlock(bb.getName() + ".inline.after", caller);
        List<Instruction> afterInsts = new ArrayList<>(bbInsts.subList(callIdx + 1, bbInsts.size()));
        bbInsts.subList(callIdx, bbInsts.size()).clear();

        for (Instruction inst : afterInsts) {
            afterBlock.getInstructions().add(inst);
            inst.setParent(afterBlock);
        }

        Map<Value, Value> valueMap = new LinkedHashMap<>();
        for (int i = 0; i < callee.getArguments().size(); i++) {
            valueMap.put(callee.getArguments().get(i), call.getOperand(i + 1));
        }

        for (BasicBlock calleeBB : new ArrayList<>(callee.getBasicBlocks())) {
            BasicBlock clonedBB = new BasicBlock(calleeBB.getName() + ".inline", caller);
            valueMap.put(calleeBB, clonedBB);
        }

        List<ReturnInst> returnInsts = new ArrayList<>();
        BasicBlock callerEntry = caller.getBasicBlocks().get(0);

        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(callee);
        List<BasicBlock> rpo = DominatorAnalysis.getReversePostOrder(callee.getBasicBlocks().get(0), cfg.successors());

        for (BasicBlock calleeBB : rpo) {
            BasicBlock clonedBB = (BasicBlock)valueMap.get(calleeBB);
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

        for (BasicBlock calleeBB : rpo) {
            BasicBlock clonedBB = (BasicBlock)valueMap.get(calleeBB);
            for (int j = 0, clonedIdx = 0; j < calleeBB.getInstructions().size(); j++) {
                Instruction inst = calleeBB.getInstructions().get(j);
                if (inst instanceof AllocaInst)
                    continue;
                Instruction clonedInst = clonedBB.getInstructions().get(clonedIdx++);
                if (inst instanceof PhiInst phi) {
                    PhiInst clonedPhi = (PhiInst)clonedInst;
                    for (Map.Entry<BasicBlock, Value> entry : phi.getIncoming().entrySet()) {
                        clonedPhi.setIncoming((BasicBlock)valueMap.get(entry.getKey()),
                            map(entry.getValue(), valueMap));
                    }
                }
            }
        }

        new BrInst((BasicBlock)valueMap.get(callee.getBasicBlocks().get(0)), bb);

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

        if (((FuncType)callee.getValueType()).getReturnType() instanceof NoneType) {
            for (ReturnInst ret : returnInsts) {
                BasicBlock retBB = ret.getParent();
                retBB.getInstructions().remove(ret);
                new BrInst(afterBlock, retBB);
            }
            call.replaceAllUsesWith(null);
        } else {
            PhiInst resPhi = new PhiInst(((FuncType)callee.getValueType()).getReturnType(), "inline.res", afterBlock);
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
            return new BinaryInst(binary.getOpCode(), map(binary.getOperand(0), valueMap),
                map(binary.getOperand(1), valueMap), newParent);
        } else if (inst instanceof IcmpInst icmp) {
            return new IcmpInst(icmp.getPredicate(), map(icmp.getOperand(0), valueMap),
                map(icmp.getOperand(1), valueMap), newParent);
        } else if (inst instanceof BrInst br) {
            if (br.getNumOperands() == 1) {
                return new BrInst((BasicBlock)map(br.getOperand(0), valueMap), newParent);
            } else {
                return new BrInst(map(br.getOperand(0), valueMap), (BasicBlock)map(br.getOperand(1), valueMap),
                    (BasicBlock)map(br.getOperand(2), valueMap), newParent);
            }
        } else if (inst instanceof CallInst call) {
            Function func = (Function)call.getOperand(0);
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
