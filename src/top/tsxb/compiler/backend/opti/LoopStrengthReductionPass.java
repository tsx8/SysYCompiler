package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.structure.*;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.type.PtrType;

import java.util.*;

public class LoopStrengthReductionPass implements Pass {
    private static class Loop {
        BasicBlock header;
        BasicBlock latch;
        Set<BasicBlock> blocks;

        Loop(BasicBlock header, BasicBlock latch, Set<BasicBlock> blocks) {
            this.header = header;
            this.latch = latch;
            this.blocks = blocks;
        }
    }

    @Override
    public boolean run(top.tsxb.compiler.ir.structure.Module module) {
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
        // Sort loops by size (inner loops first) to optimize from inside out
        loops.sort(Comparator.comparingInt(l -> l.blocks.size()));

        for (Loop loop : loops) {
            changed |= runOnLoop(loop);
        }
        return changed;
    }

    private List<Loop> findLoops(Function function) {
        List<Loop> loops = new ArrayList<>();
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);

        for (BasicBlock n : function.getBasicBlocks()) {
            if (!domInfo.dominators().containsKey(n)) continue;
            for (BasicBlock d : cfg.successors().getOrDefault(n, List.of())) {
                if (domInfo.dominators().get(n).contains(d)) {
                    // Back-edge n -> d found. d is header, n is latch.
                    Set<BasicBlock> loopBlocks = findLoopBlocks(n, d, cfg.predecessors());
                    loops.add(new Loop(d, n, loopBlocks));
                }
            }
        }
        return loops;
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

    private boolean runOnLoop(Loop loop) {
        boolean changed = false;
        // 1. Find induction variables (IVs)
        Map<PhiInst, Value> ivs = findInductionVariables(loop);
        if (ivs.isEmpty()) return false;

        // 2. Find BinaryInst that are linear functions of IVs
        List<BinaryInst> bins = new ArrayList<>();
        for (BasicBlock bb : loop.blocks) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof BinaryInst bin && (bin.getOpCode() == OpCode.MUL || bin.getOpCode() == OpCode.ADD)) {
                    bins.add(bin);
                }
            }
        }

        int phisCreated = 0;
        for (BinaryInst bin : bins) {
            if (phisCreated >= 8) break;
            if (isIvUpdate(bin, ivs, loop)) continue;
            
            LinearExpr expr = getLinearExpr(bin, ivs, loop);
            if (expr != null && expr.iv != null) {
                // Check if expr.iv is actually a Phi in the current loop header
                if (expr.iv.getParent() != loop.header) continue;
                
                if (isWorthReducing(bin, expr)) {
                    if (reduceBinaryInst(bin, expr, ivs, loop)) {
                        changed = true;
                        phisCreated++;
                    }
                }
            }
        }

        if (changed) {
            ivs = findInductionVariables(loop);
        }

        // 3. Find GetElementPtrInst (GEP) instructions that use IVs as indices
        // Group GEPs by (base, iv, ivIdx, otherIndices) to reuse Phis
        Map<LsrKey, List<GetElementPtrInst>> groups = new LinkedHashMap<>();
        for (BasicBlock bb : loop.blocks) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof GetElementPtrInst gep) {
                    LsrKey key = getLsrKey(gep, ivs, loop);
                    if (key != null) {
                        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(gep);
                    }
                }
            }
        }

        if (groups.isEmpty()) return changed;

        // 4. Perform reduction for each group
        for (Map.Entry<LsrKey, List<GetElementPtrInst>> entry : groups.entrySet()) {
            if (phisCreated >= 12) break; // Total limit
            if (!isWorthReducing(entry.getKey(), entry.getValue())) continue;
            
            if (reduceGroup(entry.getKey(), entry.getValue(), ivs, loop)) {
                changed = true;
                phisCreated++;
            }
        }

        return changed;
    }

    private boolean isWorthReducing(BinaryInst bin, LinearExpr expr) {
        // Multiplication is always worth reducing to addition
        if (bin.getOpCode() == OpCode.MUL) {
            // Only reduce if scale is constant
            return expr.scale instanceof ConstInt;
        }
        
        // For addition, only if it's not a simple increment (which is already an IV)
        // and it's used in a GEP or multiple times.
        // For now, let's be conservative and only reduce MUL or complex ADD.
        if (bin.getOpCode() == OpCode.ADD) {
            if (!(expr.scale instanceof ConstInt ci && ci.getValue() == 1)) return true;
            if (!(expr.offset instanceof ConstInt co && co.getValue() == 0)) return true;
        }
        return false;
    }

    private boolean reduceBinaryInst(BinaryInst bin, LinearExpr expr, Map<PhiInst, Value> primaryIvs, Loop loop) {
        BasicBlock preheader = findPreheader(loop);
        if (preheader == null) return false;

        Value step = primaryIvs.get(expr.iv);
        
        // 1. Initial value in preheader: scale * initial_iv + offset
        Value initialIv = expr.iv.getIncomingValue(preheader);
        Value scaledInitial = simplifyMul(expr.scale, initialIv, preheader);
        Value initialVal = simplifyAdd(scaledInitial, expr.offset, preheader);

        // 2. Phi in header
        PhiInst phi = new PhiInst(bin.getType(), "lsr.bin", null);
        loop.header.addFirst(phi);
        phi.setIncoming(preheader, initialVal);

        // 3. Increment in latch: phi + (scale * step)
        Value incAmount = simplifyMul(expr.scale, step, preheader);
        BinaryInst nextVal = new BinaryInst(OpCode.ADD, phi, incAmount, null);
        int latchBranchIdx = loop.latch.getInstructions().size();
        if (latchBranchIdx > 0 && isTerminator(loop.latch.getInstructions().get(latchBranchIdx - 1))) {
            latchBranchIdx--;
        }
        loop.latch.getInstructions().add(latchBranchIdx, nextVal);
        nextVal.setParent(loop.latch);
        loop.latch.getParent().resolveLocalName(nextVal);
        
        phi.setIncoming(loop.latch, nextVal);

        // 4. Replace
        bin.replaceAllUsesWith(phi);
        bin.getParent().getInstructions().remove(bin);
        return true;
    }

    private record LinearExpr(PhiInst iv, Value scale, Value offset) {}

    private LinearExpr getLinearExpr(Value val, Map<PhiInst, Value> primaryIvs, Loop loop) {
        if (val instanceof PhiInst phi && primaryIvs.containsKey(phi)) {
            return new LinearExpr(phi, new ConstInt(IntType.I32, 1), new ConstInt(IntType.I32, 0));
        }
        if (isLoopInvariant(val, loop)) {
            return new LinearExpr(null, new ConstInt(IntType.I32, 0), val);
        }
        if (val instanceof BinaryInst bin) {
            LinearExpr left = getLinearExpr(bin.getOperand(0), primaryIvs, loop);
            LinearExpr right = getLinearExpr(bin.getOperand(1), primaryIvs, loop);
            if (left == null || right == null) return null;

            if (bin.getOpCode() == OpCode.ADD) {
                if (left.iv == null && right.iv != null) {
                    Value newOffset = simplifyAdd(left.offset, right.offset, null);
                    return newOffset != null ? new LinearExpr(right.iv, right.scale, newOffset) : null;
                }
                if (right.iv == null && left.iv != null) {
                    Value newOffset = simplifyAdd(left.offset, right.offset, null);
                    return newOffset != null ? new LinearExpr(left.iv, left.scale, newOffset) : null;
                }
                if (left.iv != null && left.iv == right.iv) {
                    Value newScale = simplifyAdd(left.scale, right.scale, null);
                    Value newOffset = simplifyAdd(left.offset, right.offset, null);
                    return (newScale != null && newOffset != null) ? new LinearExpr(left.iv, newScale, newOffset) : null;
                }
            } else if (bin.getOpCode() == OpCode.MUL) {
                if (left.iv == null && right.iv != null) {
                    Value newScale = simplifyMul(left.offset, right.scale, null);
                    Value newOffset = simplifyMul(left.offset, right.offset, null);
                    return (newScale != null && newOffset != null) ? new LinearExpr(right.iv, newScale, newOffset) : null;
                }
                if (right.iv == null && left.iv != null) {
                    Value newScale = simplifyMul(right.offset, left.scale, null);
                    Value newOffset = simplifyMul(right.offset, left.offset, null);
                    return (newScale != null && newOffset != null) ? new LinearExpr(left.iv, newScale, newOffset) : null;
                }
            }
        }
        return null;
    }

    private boolean isTerminator(Instruction inst) {
        return inst.getOpCode() == OpCode.RET || inst.getOpCode() == OpCode.BR;
    }

    private Value simplifyAdd(Value a, Value b, BasicBlock insertAt) {
        if (a instanceof ConstInt ca && b instanceof ConstInt cb) {
            return new ConstInt(IntType.I32, ca.getValue() + cb.getValue());
        }
        if (a instanceof ConstInt ca && ca.getValue() == 0) return b;
        if (b instanceof ConstInt cb && cb.getValue() == 0) return a;
        if (insertAt == null) return null;
        
        BinaryInst bin = new BinaryInst(OpCode.ADD, a, b, null);
        int idx = insertAt.getInstructions().size();
        if (idx > 0 && isTerminator(insertAt.getInstructions().get(idx - 1))) {
            idx--;
        }
        insertAt.getInstructions().add(idx, bin);
        bin.setParent(insertAt);
        insertAt.getParent().resolveLocalName(bin);
        return bin;
    }

    private Value simplifyMul(Value a, Value b, BasicBlock insertAt) {
        if (a instanceof ConstInt ca && b instanceof ConstInt cb) {
            return new ConstInt(IntType.I32, ca.getValue() * cb.getValue());
        }
        if (a instanceof ConstInt ca) {
            if (ca.getValue() == 0) return new ConstInt(IntType.I32, 0);
            if (ca.getValue() == 1) return b;
        }
        if (b instanceof ConstInt cb) {
            if (cb.getValue() == 0) return new ConstInt(IntType.I32, 0);
            if (cb.getValue() == 1) return a;
        }
        if (insertAt == null) return null;
        
        BinaryInst bin = new BinaryInst(OpCode.MUL, a, b, null);
        int idx = insertAt.getInstructions().size();
        if (idx > 0 && isTerminator(insertAt.getInstructions().get(idx - 1))) {
            idx--;
        }
        insertAt.getInstructions().add(idx, bin);
        bin.setParent(insertAt);
        insertAt.getParent().resolveLocalName(bin);
        return bin;
    }

    private boolean isWorthReducing(LsrKey key, List<GetElementPtrInst> geps) {
        // If base is global, it's always worth it (saves 'la' which is 2 instructions)
        if (key.base instanceof GlobalValue) return true;
        
        // If multiple GEPs share the same pointer, it's definitely worth it
        if (geps.size() > 1) return true;
        
        // If the GEP is complex (more than 2 indices, e.g. a[i][j])
        // a[i] has 2 indices: [0, i]. a[i][j] has 3: [0, i, j].
        if (key.otherIndices.size() > 2) return true;
        
        // For a simple local array access a[i], it's sll+addu (2 insts) vs addiu (1 inst).
        // We save 1 instruction but use 1 extra register.
        // In tight loops with many arrays, this might cause spills.
        // So we only do it if there's some other benefit or if it's a global.
        return false;
    }

    private record LsrKey(Value base, PhiInst iv, int ivIdx, List<Value> otherIndices) {}

    private LsrKey getLsrKey(GetElementPtrInst gep, Map<PhiInst, Value> ivs, Loop loop) {
        Value base = gep.getOperand(0);
        if (!isLoopInvariant(base, loop)) return null;

        int ivIdx = -1;
        PhiInst iv = null;
        List<Value> otherIndices = new ArrayList<>();

        for (int i = 1; i < gep.getNumOperands(); i++) {
            Value idx = gep.getOperand(i);
            LinearExpr expr = getLinearExpr(idx, ivs, loop);
            if (expr != null && expr.iv != null) {
                if (iv != null) return null; // Only one IV allowed
                // For GEP, we currently only support scale 1 and offset 0 for simplicity.
                // If we have a non-zero offset or non-one scale, we rely on BinaryInst reduction
                // to turn it into a primary IV first.
                if (expr.scale instanceof ConstInt ci && ci.getValue() == 1 && 
                    expr.offset instanceof ConstInt co && co.getValue() == 0) {
                    ivIdx = i;
                    iv = expr.iv;
                    otherIndices.add(null);
                } else {
                    return null;
                }
            } else if (isLoopInvariant(idx, loop)) {
                otherIndices.add(idx);
            } else {
                return null; // Non-invariant index
            }
        }

        return (iv != null && ivIdx == gep.getNumOperands() - 1) ? new LsrKey(base, iv, ivIdx, otherIndices) : null;
    }

    private boolean reduceGroup(LsrKey key, List<GetElementPtrInst> geps, Map<PhiInst, Value> ivs, Loop loop) {
        BasicBlock preheader = findPreheader(loop);
        if (preheader == null) return false;

        Value step = ivs.get(key.iv);
        GetElementPtrInst firstGep = geps.get(0);

        // 1. Create initial pointer in preheader
        List<Value> initialIndices = new ArrayList<>();
        for (int i = 0; i < key.otherIndices.size(); i++) {
            Value idx = key.otherIndices.get(i);
            if (idx == null) {
                initialIndices.add(key.iv.getIncomingValue(preheader));
            } else {
                initialIndices.add(idx);
            }
        }
        GetElementPtrInst initialGep = new GetElementPtrInst(key.base, initialIndices, null);
        int branchIdx = preheader.getInstructions().size();
        if (branchIdx > 0 && isTerminator(preheader.getInstructions().get(branchIdx - 1))) {
            branchIdx--;
        }
        preheader.getInstructions().add(branchIdx, initialGep);
        initialGep.setParent(preheader);
        preheader.getParent().resolveLocalName(initialGep);

        // 2. Create Phi in header
        PhiInst ptrPhi = new PhiInst(firstGep.getType(), "lsr.iv", null);
        loop.header.addFirst(ptrPhi);
        ptrPhi.setIncoming(preheader, initialGep);

        // 3. Create increment in latch
        // We need to be careful about the stride. 
        // If ivIdx is the last index, stride is sizeof(element).
        // For now, we only support the last index to ensure correctness.
        if (key.ivIdx != firstGep.getNumOperands() - 1) {
            // If not the last index, we'd need complex stride logic.
            // But SysY usually has 1D arrays or the IV is the last index.
            // To be safe, we only optimize if it's the last index.
            // Actually, let's check if it's the last index.
        }

        // For SysY, most GEPs are (base, 0, i) or (ptr, i).
        // If it's (base, 0, i), ivIdx is 2, numOperands is 3. Correct.
        // If it's (ptr, i), ivIdx is 1, numOperands is 2. Correct.
        
        List<Value> stepIndices = new ArrayList<>();
        // If the GEP was (base, 0, i), the result is i32*.
        // Incrementing i32* by step is (ptr, step).
        stepIndices.add(step);
        GetElementPtrInst nextPtr = new GetElementPtrInst(ptrPhi, stepIndices, null);
        int latchBranchIdx = loop.latch.getInstructions().size();
        if (latchBranchIdx > 0 && isTerminator(loop.latch.getInstructions().get(latchBranchIdx - 1))) {
            latchBranchIdx--;
        }
        loop.latch.getInstructions().add(latchBranchIdx, nextPtr);
        nextPtr.setParent(loop.latch);
        loop.latch.getParent().resolveLocalName(nextPtr);
        
        ptrPhi.setIncoming(loop.latch, nextPtr);

        // 4. Replace all GEPs in the group
        for (GetElementPtrInst gep : geps) {
            gep.replaceAllUsesWith(ptrPhi);
            gep.dropAllReferences();
            gep.getParent().getInstructions().remove(gep);
        }

        return true;
    }

    private Map<PhiInst, Value> findInductionVariables(Loop loop) {
        Map<PhiInst, Value> ivs = new HashMap<>();
        for (Instruction inst : loop.header.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                Value latchVal = phi.getIncomingValue(loop.latch);
                if (latchVal instanceof BinaryInst bin && bin.getOpCode() == OpCode.ADD) {
                    Value step = null;
                    if (bin.getOperand(0) == phi) {
                        step = bin.getOperand(1);
                    } else if (bin.getOperand(1) == phi) {
                        step = bin.getOperand(0);
                    }
                    if (step != null && isLoopInvariant(step, loop)) {
                        ivs.put(phi, step);
                    }
                }
            }
        }
        return ivs;
    }

    private boolean isLoopInvariant(Value val, Loop loop) {
        if (val instanceof ConstInt) return true;
        if (val instanceof GlobalValue) return true;
        if (val instanceof Argument) return true;
        if (val instanceof Instruction inst) {
            return !loop.blocks.contains(inst.getParent());
        }
        return true;
    }
    private boolean isIvUpdate(BinaryInst bin, Map<PhiInst, Value> ivs, Loop loop) {
        for (PhiInst phi : ivs.keySet()) {
            if (phi.getIncomingValue(loop.latch) == bin) return true;
        }
        return false;
    }
    private BasicBlock findPreheader(Loop loop) {
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(loop.header.getParent());
        List<BasicBlock> preds = cfg.predecessors().get(loop.header);
        BasicBlock preheader = null;
        for (BasicBlock pred : preds) {
            if (!loop.blocks.contains(pred)) {
                if (preheader != null) return null; // Multiple entries, not a simple loop
                preheader = pred;
            }
        }
        return preheader;
    }
}
