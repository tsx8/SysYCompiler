package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.CallInst;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Argument;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.constant.Constant;

import java.util.*;

public class LiveIntervalAnalysis {
    private final Function function;
    private final LivenessAnalysis liveness;
    private final LoopAnalysis loopAnalysis;
    private final List<Instruction> linearizedInsts = new ArrayList<>();
    private final Map<Instruction, Integer> instToId = new LinkedHashMap<>();
    private final Map<Value, LiveInterval> intervals = new LinkedHashMap<>();
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
        checkSpansCall();
        collectHints();
    }

    private void collectHints() {
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    LiveInterval phiInterval = intervals.get(phi);
                    if (phiInterval == null) continue;

                    for (int i = 0; i < phi.getNumOperands(); i++) {
                        Value incoming = phi.getOperand(i);
                        LiveInterval incomingInterval = intervals.get(incoming);
                        if (incomingInterval != null) {
                            phiInterval.addHint(incomingInterval);
                            incomingInterval.addHint(phiInterval);
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
                linearizedInsts.add(inst);
                instToId.put(inst, id);
                if (inst instanceof CallInst) {
                    callInstIds.add(id);
                }
                id += 2; // Use step 2 to allow insertions if needed
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

        // Initialize intervals for global variables used in this function
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                for (int i = 0; i < inst.getNumOperands(); i++) {
                    Value op = inst.getOperand(i);
                    if (op instanceof GlobalVariable gv) {
                        LiveInterval interval = getOrCreateInterval(gv);
                        if (interval.getStart() == Integer.MAX_VALUE) {
                            interval.setStart(firstInstId);
                            interval.setEnd(firstInstId);
                        }
                    }
                }
            }
        }

        // Process blocks in reverse order
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
        Collections.reverse(blocks);

        for (BasicBlock bb : blocks) {
            int bStart = blockStart.get(bb);
            int bEnd = blockEnd.get(bb);
            double weight = Math.pow(10, loopAnalysis.getLoopDepth(bb));

            // Live out values are live until the end of the block
            for (Value v : liveness.getLiveOut(bb)) {
                getOrCreateInterval(v).addRange(bStart, bEnd);
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

                // Use
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
        if (val == null) return false;
        if (val instanceof GlobalVariable) return true;
        if (val instanceof Constant || val instanceof BasicBlock) return false;
        return !(val instanceof Instruction inst) || !(inst.getType() instanceof NoneType);
    }

    public List<LiveInterval> getIntervals() {
        List<LiveInterval> result = new ArrayList<>(intervals.values());
        Collections.sort(result);
        return result;
    }

    public Map<Value, LiveInterval> getIntervalMap() {
        return intervals;
    }

    public Map<Instruction, Integer> getInstToId() {
        return instToId;
    }
}
