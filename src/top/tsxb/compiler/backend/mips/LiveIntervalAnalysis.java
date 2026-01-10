package top.tsxb.compiler.backend.mips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.constant.Constant;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.CallInst;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.GetElementPtrInst;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.structure.Argument;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.type.NoneType;

public class LiveIntervalAnalysis {
    private final Function function;
    private final LivenessAnalysis liveness;
    private final LoopAnalysis loopAnalysis;
    private final Map<Instruction, Integer> instToId = new LinkedHashMap<>();
    private final Map<Value, LiveInterval> intervals = new LinkedHashMap<>();
    private final Map<Value, java.util.Set<Value>> interference = new LinkedHashMap<>();
    private final Map<BasicBlock, Integer> blockStart = new LinkedHashMap<>();
    private final Map<BasicBlock, Integer> blockEnd = new LinkedHashMap<>();
    private final List<Integer> callInstIds = new ArrayList<>();

    public LiveIntervalAnalysis(Function function, LivenessAnalysis liveness, LoopAnalysis loopAnalysis) {
        this.function = function;
        this.liveness = liveness;
        this.loopAnalysis = loopAnalysis;
    }

    public void analyze() {
        linearize();
        computeIntervals();
        computeInterference();
        checkSpansCall();
        collectHints();
    }

    private void addInterference(Value a, Value b) {
        if (a == null || b == null || a == b)
            return;
        if (!isAllocatable(a) || !isAllocatable(b))
            return;
        if (!intervals.containsKey(a) || !intervals.containsKey(b))
            return;

        interference.computeIfAbsent(a, k -> new java.util.LinkedHashSet<>()).add(b);
        interference.computeIfAbsent(b, k -> new java.util.LinkedHashSet<>()).add(a);
    }

    private void computeInterference() {
        // For entry block, add a clique for all live-in values (arguments have no defining instruction).
        if (!function.getBasicBlocks().isEmpty()) {
            BasicBlock entry = function.getBasicBlocks().get(0);
            java.util.List<Value> liveAtEntry = new java.util.ArrayList<>();
            for (Value v : liveness.getLiveIn(entry)) {
                if (isAllocatable(v) && intervals.containsKey(v)) {
                    liveAtEntry.add(v);
                }
            }
            for (int i = 0; i < liveAtEntry.size(); i++) {
                for (int j = i + 1; j < liveAtEntry.size(); j++) {
                    addInterference(liveAtEntry.get(i), liveAtEntry.get(j));
                }
            }
        }

        // Build interference edges by scanning instructions backwards with block liveOut as seed.
        for (BasicBlock bb : function.getBasicBlocks()) {
            java.util.Set<Value> live = new java.util.LinkedHashSet<>();
            for (Value v : liveness.getLiveOut(bb)) {
                if (isAllocatable(v) && intervals.containsKey(v)) {
                    live.add(v);
                }
            }

            java.util.List<Instruction> insts = bb.getInstructions();
            for (int i = insts.size() - 1; i >= 0; i--) {
                Instruction inst = insts.get(i);

                // Def: add interference between the definition and values live *after* this instruction.
                if (isAllocatable(inst) && intervals.containsKey(inst)) {
                    for (Value v : live) {
                        addInterference(inst, v);
                    }
                    live.remove(inst);
                }

                // Uses
                if (!(inst instanceof PhiInst)) {
                    if (inst instanceof BrInst br) {
                        IcmpInst icmp = IcmpBranchFusion.getFusableIcmpFromBr(br);
                        if (icmp != null) {
                            Value lhs = icmp.getOperand(0);
                            if (isAllocatable(lhs) && intervals.containsKey(lhs)) {
                                live.add(lhs);
                            }
                            Value rhs = icmp.getOperand(1);
                            if (isAllocatable(rhs) && intervals.containsKey(rhs)) {
                                live.add(rhs);
                            }
                            continue;
                        }
                    }
                    for (int j = 0; j < inst.getNumOperands(); j++) {
                        Value op = inst.getOperand(j);
                        if (isAllocatable(op) && intervals.containsKey(op)) {
                            live.add(op);
                        }
                    }
                }
            }
        }
    }

    private void collectHints() {
        for (BasicBlock bb : function.getBasicBlocks()) {
            List<PhiInst> blockPhis = new ArrayList<>();
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    blockPhis.add(phi);
                } else {
                    break;
                }
            }

            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    LiveInterval phiInterval = intervals.get(phi);
                    if (phiInterval == null)
                        continue;

                    for (int i = 0; i < phi.getNumOperands(); i++) {
                        Value incoming = phi.getOperand(i);
                        LiveInterval incomingInterval = intervals.get(incoming);
                        if (incomingInterval != null) {
                            phiInterval.addPhiHint(incomingInterval);
                            incomingInterval.addPhiHint(phiInterval);
                        }
                    }

                    if (loopAnalysis.isLoopHeader(bb) && !blockPhis.isEmpty()) {
                        for (var entry : phi.getIncoming().entrySet()) {
                            BasicBlock pred = entry.getKey();
                            if (loopAnalysis.getLoopDepth(pred) <= 0) {
                                continue;
                            }
                            Value incoming = entry.getValue();
                            LiveInterval incomingInterval = intervals.get(incoming);
                            if (incomingInterval == null) {
                                continue;
                            }
                            for (PhiInst otherPhi : blockPhis) {
                                if (otherPhi == phi) {
                                    continue;
                                }
                                LiveInterval otherInterval = intervals.get(otherPhi);
                                if (otherInterval == null) {
                                    continue;
                                }
                                incomingInterval.addAvoidSameRegWith(otherInterval);
                                otherInterval.addAvoidSameRegWith(incomingInterval);
                            }
                        }
                    }
                }
            }
        }
    }

    private void linearize() {
        int id = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            blockStart.put(bb, id);
            for (Instruction inst : bb.getInstructions()) {
                instToId.put(inst, id);
                if (inst instanceof CallInst) {
                    callInstIds.add(id);
                }
                id += 2;
            }
            blockEnd.put(bb, id - 1);
        }
    }

    private void checkSpansCall() {
        for (LiveInterval interval : intervals.values()) {
            for (int callId : callInstIds) {
                if (callId > interval.getStart() && callId < interval.getEnd()) {
                    interval.setSpansCall(true);
                    break;
                }
            }
        }
    }

    private void computeIntervals() {
        // Initialize intervals for arguments
        int firstInstId = -1;
        for (Argument arg : function.getArguments()) {
            LiveInterval interval = new LiveInterval(arg);
            interval.setStart(firstInstId);
            interval.setEnd(firstInstId);
            intervals.put(arg, interval);
        }

        // Process blocks in reverse order
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
        Collections.reverse(blocks);

        for (BasicBlock bb : blocks) {
            int bStart = blockStart.get(bb);
            int bEnd = blockEnd.get(bb);
            double weight = Math.pow(10, loopAnalysis.getLoopDepth(bb));

            for (Value v : liveness.getLiveOut(bb)) {
                if (isAllocatable(v)) {
                    getOrCreateInterval(v).addRange(bEnd, bEnd);
                }
            }

            for (Value v : liveness.getLiveIn(bb)) {
                if (isAllocatable(v)) {
                    getOrCreateInterval(v).addRange(bStart, bStart);
                }
            }

            // Process instructions in reverse
            List<Instruction> insts = bb.getInstructions();
            for (int i = insts.size() - 1; i >= 0; i--) {
                Instruction inst = insts.get(i);
                int instId = instToId.get(inst);

                // Def
                if (isAllocatable(inst)) {
                    LiveInterval interval = getOrCreateInterval(inst);
                    interval.setStart(instId);
                    interval.addWeight(weight);
                    // If it was not live-out, it ends here
                    if (interval.getEnd() == Integer.MIN_VALUE) {
                        interval.setEnd(instId);
                    }
                }

                if (inst instanceof PhiInst) {
                    continue;
                }
                if (inst instanceof BrInst br) {
                    IcmpInst icmp = IcmpBranchFusion.getFusableIcmpFromBr(br);
                    if (icmp != null) {
                        Value lhs = icmp.getOperand(0);
                        if (isAllocatable(lhs)) {
                            LiveInterval interval = getOrCreateInterval(lhs);
                            interval.addRange(bStart, instId);
                            interval.addWeight(weight);
                        }
                        Value rhs = icmp.getOperand(1);
                        if (isAllocatable(rhs)) {
                            LiveInterval interval = getOrCreateInterval(rhs);
                            interval.addRange(bStart, instId);
                            interval.addWeight(weight);
                        }
                        continue;
                    }
                }
                for (int j = 0; j < inst.getNumOperands(); j++) {
                    Value op = inst.getOperand(j);
                    if (isAllocatable(op)) {
                        LiveInterval interval = getOrCreateInterval(op);
                        interval.addRange(bStart, instId);
                        interval.addWeight(weight);
                    }
                }
            }
        }
    }

    private LiveInterval getOrCreateInterval(Value v) {
        return intervals.computeIfAbsent(v, LiveInterval::new);
    }

    private boolean isAllocatable(Value val) {
        if (val == null)
            return false;
        if (val instanceof GlobalVariable)
            return false;
        if (val instanceof top.tsxb.compiler.ir.inst.AllocaInst)
            return false;
        if (val instanceof IcmpInst icmp && IcmpBranchFusion.isFusableIcmp(icmp))
            return false;
        if (val instanceof Constant || val instanceof BasicBlock)
            return false;
        // Rematerializable constant-address GEPs (e.g. strings) are cheaper to recompute than to keep live/spill.
        if (val instanceof GetElementPtrInst gep && isRematerializableConstGep(gep)) {
            return false;
        }
        return !(val instanceof Instruction inst) || !(inst.getType() instanceof NoneType);
    }

    private boolean isRematerializableConstGep(GetElementPtrInst gep) {
        // Only handle pure constant address computations: base is ultimately a global, all indices are constants.
        Value base = gep.getOperand(0);
        while (base instanceof GetElementPtrInst nested) {
            for (int i = 1; i < nested.getNumOperands(); i++) {
                if (!(nested.getOperand(i) instanceof ConstInt)) {
                    return false;
                }
            }
            base = nested.getOperand(0);
        }
        if (!(base instanceof GlobalVariable)) {
            return false;
        }
        for (int i = 1; i < gep.getNumOperands(); i++) {
            if (!(gep.getOperand(i) instanceof ConstInt)) {
                return false;
            }
        }
        return true;
    }

    public List<LiveInterval> getIntervals() {
        List<LiveInterval> result = new ArrayList<>();
        for (LiveInterval interval : intervals.values()) {
            if (interval.getStart() < interval.getEnd()) {
                result.add(interval);
            }
        }
        Collections.sort(result);
        return result;
    }

    public Map<Value, java.util.Set<Value>> getInterference() {
        return interference;
    }

}
