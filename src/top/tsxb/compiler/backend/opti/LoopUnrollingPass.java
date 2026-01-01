package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.IntType;

import java.util.*;

public class LoopUnrollingPass implements Pass {
    private static final int MAX_TRIP_COUNT = 20;
    private static final int MAX_TOTAL_INSTS = 100;

    private static class Loop {
        BasicBlock header;
        BasicBlock latch;
        Set<BasicBlock> blocks;
        BasicBlock exit;

        Loop(BasicBlock header, BasicBlock latch, Set<BasicBlock> blocks, BasicBlock exit) {
            this.header = header;
            this.latch = latch;
            this.blocks = blocks;
            this.exit = exit;
        }
    }

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
        List<Loop> loops = findLoops(function);
        // Sort loops by size (inner loops first)
        loops.sort(Comparator.comparingInt(l -> l.blocks.size()));

        for (Loop loop : loops) {
            if (tryUnroll(loop)) {
                return true;
            }
        }
        return changed;
    }

    private List<Loop> findLoops(Function function) {
        List<Loop> loops = new ArrayList<>();
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);

        for (BasicBlock n : function.getBasicBlocks()) {
            for (BasicBlock d : cfg.successors().getOrDefault(n, List.of())) {
                if (domInfo.dominators().get(n).contains(d)) {
                    // Back-edge n -> d found. d is header, n is latch.
                    Set<BasicBlock> loopBlocks = findLoopBlocks(n, d, cfg.predecessors());
                    BasicBlock exit = findSingleExit(loopBlocks, cfg.successors());
                    if (exit != null && isSimpleLoop(loopBlocks, cfg.successors())) {
                        loops.add(new Loop(d, n, loopBlocks, exit));
                    }
                }
            }
        }
        return loops;
    }

    private boolean isSimpleLoop(Set<BasicBlock> loopBlocks, Map<BasicBlock, List<BasicBlock>> successors) {
        int exitEdges = 0;
        for (BasicBlock bb : loopBlocks) {
            for (BasicBlock succ : successors.getOrDefault(bb, List.of())) {
                if (!loopBlocks.contains(succ)) {
                    exitEdges++;
                }
            }
        }
        return exitEdges == 1;
    }

    private Set<BasicBlock> findLoopBlocks(BasicBlock latch, BasicBlock header, Map<BasicBlock, List<BasicBlock>> predecessors) {
        Set<BasicBlock> blocks = new LinkedHashSet<>();
        blocks.add(header);
        blocks.add(latch);
        if (latch == header) return blocks;

        Queue<BasicBlock> queue = new LinkedList<>();
        queue.add(latch);
        while (!queue.isEmpty()) {
            BasicBlock curr = queue.poll();
            for (BasicBlock pred : predecessors.getOrDefault(curr, List.of())) {
                if (!blocks.contains(pred)) {
                    blocks.add(pred);
                    queue.add(pred);
                }
            }
        }
        return blocks;
    }

    private BasicBlock findSingleExit(Set<BasicBlock> loopBlocks, Map<BasicBlock, List<BasicBlock>> successors) {
        Set<BasicBlock> exits = new LinkedHashSet<>();
        for (BasicBlock bb : loopBlocks) {
            for (BasicBlock succ : successors.getOrDefault(bb, List.of())) {
                if (!loopBlocks.contains(succ)) {
                    exits.add(succ);
                }
            }
        }
        return exits.size() == 1 ? exits.iterator().next() : null;
    }

    private boolean tryUnroll(Loop loop) {
        // Only unroll simple loops for now
        if (loop.blocks.size() != 2) return false;

        // 1. Find induction variable
        PhiInst iv = null;
        for (Instruction inst : loop.header.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                Value latchVal = phi.getIncomingValue(loop.latch);
                if (latchVal instanceof BinaryInst bin && bin.getOpCode() == OpCode.ADD) {
                    if (bin.getOperand(0) == phi || bin.getOperand(1) == phi) {
                        iv = phi;
                        break;
                    }
                }
            }
        }
        if (iv == null) return false;

        // 2. Find loop condition
        Instruction last = loop.header.getInstructions().get(loop.header.getInstructions().size() - 1);
        if (!(last instanceof BrInst br) || br.getNumOperands() != 3) return false;
        Value cond = br.getOperand(0);
        if (!(cond instanceof IcmpInst icmp)) return false;

        // 3. Analyze trip count
        Integer tripCount = calculateTripCount(iv, icmp, loop);
        if (tripCount == null || tripCount <= 0 || tripCount > MAX_TRIP_COUNT) return false;

        // 4. Check total instructions
        int bodyInsts = 0;
        for (BasicBlock bb : loop.blocks) {
            bodyInsts += bb.getInstructions().size();
        }
        if (bodyInsts * tripCount > MAX_TOTAL_INSTS) return false;

        // 5. Perform unrolling
        unroll(loop, iv, tripCount);
        return true;
    }

    private Integer calculateTripCount(PhiInst iv, IcmpInst icmp, Loop loop) {
        Value initVal = null;
        for (Map.Entry<BasicBlock, Value> entry : iv.getIncoming().entrySet()) {
            if (!loop.blocks.contains(entry.getKey())) {
                initVal = entry.getValue();
                break;
            }
        }
        if (!(initVal instanceof ConstInt constInit)) return null;
        int init = constInit.getValue();

        Value nextVal = iv.getIncomingValue(loop.latch);
        if (!(nextVal instanceof BinaryInst bin) || bin.getOpCode() != OpCode.ADD) return null;
        Value stepVal = (bin.getOperand(0) == iv) ? bin.getOperand(1) : bin.getOperand(0);
        if (!(stepVal instanceof ConstInt constStep)) return null;
        int step = constStep.getValue();

        Value limitVal = (icmp.getOperand(0) == iv) ? icmp.getOperand(1) : icmp.getOperand(0);
        if (!(limitVal instanceof ConstInt constLimit)) return null;
        int limit = constLimit.getValue();

        IcmpInst.CondCode pred = icmp.getPredicate();
        if (icmp.getOperand(1) == iv) {
            pred = flipPredicate(pred);
        }

        int current = init;
        for (int i = 0; i <= MAX_TRIP_COUNT + 1; i++) {
            if (!evaluateCondition(current, limit, pred)) {
                return i;
            }
            current += step;
        }
        return null;
    }

    private boolean evaluateCondition(int lhs, int rhs, IcmpInst.CondCode pred) {
        return switch (pred) {
            case EQ -> lhs == rhs;
            case NE -> lhs != rhs;
            case SGT -> lhs > rhs;
            case SGE -> lhs >= rhs;
            case SLT -> lhs < rhs;
            case SLE -> lhs <= rhs;
        };
    }

    private IcmpInst.CondCode flipPredicate(IcmpInst.CondCode pred) {
        return switch (pred) {
            case EQ -> IcmpInst.CondCode.EQ;
            case NE -> IcmpInst.CondCode.NE;
            case SGT -> IcmpInst.CondCode.SLT;
            case SGE -> IcmpInst.CondCode.SLE;
            case SLT -> IcmpInst.CondCode.SGT;
            case SLE -> IcmpInst.CondCode.SGE;
        };
    }

    private void unroll(Loop loop, PhiInst iv, int tripCount) {
        Function function = loop.header.getParent();
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);

        List<BasicBlock> preHeaders = new ArrayList<>();
        for (BasicBlock pred : cfg.predecessors().getOrDefault(loop.header, List.of())) {
            if (!loop.blocks.contains(pred)) {
                preHeaders.add(pred);
            }
        }
        if (preHeaders.isEmpty()) return;
        BasicBlock preHeader = preHeaders.get(0);

        if (tripCount == 0) {
            for (BasicBlock ph : preHeaders) {
                Instruction phLast = ph.getInstructions().get(ph.getInstructions().size() - 1);
                for (int j = 0; j < phLast.getNumOperands(); j++) {
                    if (phLast.getOperand(j) == loop.header) {
                        phLast.setOperand(j, loop.exit);
                    }
                }
            }
            // Update exit Phis
            for (Instruction inst : loop.exit.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    Value loopVal = phi.getIncomingValue(loop.header);
                    if (loopVal != null) {
                        for (BasicBlock ph : preHeaders) {
                            phi.setIncoming(ph, loopVal);
                        }
                        phi.removeIncoming(loop.header);
                    }
                }
            }
            // Remove old blocks
            for (BasicBlock bb : loop.blocks) {
                for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                    inst.dropAllReferences();
                }
                function.removeBasicBlock(bb);
            }
            return;
        }

        Map<PhiInst, Value> currentPhiValues = new HashMap<>();
        for (Instruction inst : loop.header.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                currentPhiValues.put(phi, phi.getIncomingValue(preHeader));
            }
        }

        int init = ((ConstInt) iv.getIncomingValue(preHeader)).getValue();
        BinaryInst nextVal = (BinaryInst) iv.getIncomingValue(loop.latch);
        Value stepVal = (nextVal.getOperand(0) == iv) ? nextVal.getOperand(1) : nextVal.getOperand(0);
        int step = ((ConstInt) stepVal).getValue();

        List<Map<BasicBlock, BasicBlock>> allBlockMappings = new ArrayList<>();
        List<Map<Value, Value>> allValueMappings = new ArrayList<>();

        for (int i = 0; i < tripCount; i++) {
            Map<Value, Value> iterationMapping = new HashMap<>();
            Map<BasicBlock, BasicBlock> blockMapping = new HashMap<>();

            for (Map.Entry<PhiInst, Value> entry : currentPhiValues.entrySet()) {
                if (entry.getKey() == iv) {
                    iterationMapping.put(iv, new ConstInt(IntType.I32, init + i * step));
                } else {
                    iterationMapping.put(entry.getKey(), entry.getValue());
                }
            }

            for (BasicBlock bb : loop.blocks) {
                BasicBlock newBb = new BasicBlock(bb.getName() + ".u" + i, function);
                blockMapping.put(bb, newBb);
            }
            allBlockMappings.add(blockMapping);

            for (BasicBlock bb : loop.blocks) {
                BasicBlock newBb = blockMapping.get(bb);
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof PhiInst) continue;
                    Instruction newInst = copyInstruction(inst, newBb);
                    if (newInst != null) {
                        iterationMapping.put(inst, newInst);
                    }
                }
            }
            allValueMappings.add(iterationMapping);

            for (BasicBlock bb : loop.blocks) {
                BasicBlock newBb = blockMapping.get(bb);
                for (Instruction newInst : newBb.getInstructions()) {
                    for (int j = 0; j < newInst.getNumOperands(); j++) {
                        Value op = newInst.getOperand(j);
                        if (iterationMapping.containsKey(op)) {
                            newInst.setOperand(j, iterationMapping.get(op));
                        } else if (op instanceof BasicBlock && blockMapping.containsKey(op)) {
                            newInst.setOperand(j, blockMapping.get(op));
                        }
                    }
                }
            }

            for (Instruction inst : loop.header.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    Value latchVal = phi.getIncomingValue(loop.latch);
                    Value nextValue = iterationMapping.getOrDefault(latchVal, latchVal);
                    currentPhiValues.put(phi, nextValue);
                }
            }
        }

        for (BasicBlock ph : preHeaders) {
            Instruction phLast = ph.getInstructions().get(ph.getInstructions().size() - 1);
            for (int j = 0; j < phLast.getNumOperands(); j++) {
                if (phLast.getOperand(j) == loop.header) {
                    phLast.setOperand(j, allBlockMappings.get(0).get(loop.header));
                }
            }
        }

        for (int i = 0; i < tripCount; i++) {
            BasicBlock currentHeader = allBlockMappings.get(i).get(loop.header);
            BasicBlock currentLatch = allBlockMappings.get(i).get(loop.latch);
            
            Instruction headerLast = currentHeader.getInstructions().get(currentHeader.getInstructions().size() - 1);
            if (headerLast instanceof BrInst br && br.getNumOperands() == 3) {
                Instruction origHeaderLast = loop.header.getInstructions().get(loop.header.getInstructions().size() - 1);
                BasicBlock origBody = (BasicBlock) origHeaderLast.getOperand(1);
                if (!loop.blocks.contains(origBody)) {
                    origBody = (BasicBlock) origHeaderLast.getOperand(2);
                }
                
                currentHeader.getInstructions().remove(headerLast);
                br.dropAllReferences();
                new BrInst(allBlockMappings.get(i).get(origBody), currentHeader);
            }

            Instruction latchLast = currentLatch.getInstructions().get(currentLatch.getInstructions().size() - 1);
            if (i < tripCount - 1) {
                latchLast.setOperand(0, allBlockMappings.get(i + 1).get(loop.header));
            } else {
                latchLast.setOperand(0, loop.exit);
            }
        }

        for (Instruction inst : loop.exit.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                Value val = phi.getIncomingValue(loop.header);
                if (val != null) {
                    Value lastVal = allValueMappings.get(tripCount - 1).getOrDefault(val, val);
                    phi.removeIncoming(loop.header);
                    phi.setIncoming(allBlockMappings.get(tripCount - 1).get(loop.latch), lastVal);
                }
            }
        }

        for (BasicBlock bb : loop.blocks) {
            for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                Value replacement;
                if (inst instanceof PhiInst phi && bb == loop.header) {
                    replacement = currentPhiValues.get(phi);
                } else {
                    replacement = allValueMappings.get(tripCount - 1).get(inst);
                }
                if (replacement != null && replacement != inst) {
                    inst.replaceAllUsesWith(replacement);
                }
            }
        }

        for (BasicBlock bb : loop.blocks) {
            for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                inst.dropAllReferences();
            }
            function.removeBasicBlock(bb);
        }
    }

    private Instruction copyInstruction(Instruction inst, BasicBlock newParent) {
        if (inst instanceof BinaryInst bin) {
            return new BinaryInst(bin.getOpCode(), bin.getOperand(0), bin.getOperand(1), newParent);
        } else if (inst instanceof LoadInst load) {
            return new LoadInst(load.getOperand(0), newParent);
        } else if (inst instanceof StoreInst store) {
            return new StoreInst(store.getOperand(0), store.getOperand(1), newParent);
        } else if (inst instanceof GetElementPtrInst gep) {
            List<Value> indices = new ArrayList<>();
            for (int i = 1; i < gep.getNumOperands(); i++) {
                indices.add(gep.getOperand(i));
            }
            return new GetElementPtrInst(gep.getOperand(0), indices, newParent);
        } else if (inst instanceof CallInst call) {
            List<Value> args = new ArrayList<>();
            for (int i = 1; i < call.getNumOperands(); i++) {
                args.add(call.getOperand(i));
            }
            return new CallInst((Function) call.getOperand(0), args, newParent);
        } else if (inst instanceof BrInst br) {
            if (br.getNumOperands() == 3) {
                return new BrInst(br.getOperand(0), (BasicBlock) br.getOperand(1), (BasicBlock) br.getOperand(2), newParent);
            } else {
                return new BrInst((BasicBlock) br.getOperand(0), newParent);
            }
        } else if (inst instanceof IcmpInst icmp) {
            return new IcmpInst(icmp.getPredicate(), icmp.getOperand(0), icmp.getOperand(1), newParent);
        } else if (inst instanceof ZextInst zext) {
            return new ZextInst(zext.getOperand(0), zext.getType(), newParent);
        } else if (inst instanceof AllocaInst alloca) {
            return new AllocaInst(alloca.getAllocatedType(), alloca.getName(), newParent);
        } else if (inst instanceof ReturnInst ret) {
            if (ret.getNumOperands() > 0) {
                return new ReturnInst(ret.getOperand(0), newParent);
            } else {
                return new ReturnInst(newParent);
            }
        }
        return null;
    }
}
