package top.tsxb.compiler.backend.mips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.Constant;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.type.NoneType;

public class LivenessAnalysis {
    private final Function function;
    private final Map<BasicBlock, Set<Value>> liveIn = new LinkedHashMap<>();
    private final Map<BasicBlock, Set<Value>> liveOut = new LinkedHashMap<>();
    private final Map<BasicBlock, Set<Value>> def = new LinkedHashMap<>();
    private final Map<BasicBlock, Set<Value>> use = new LinkedHashMap<>();
    private final Map<BasicBlock, Set<Value>> phiUse = new LinkedHashMap<>();
    private final Map<BasicBlock, Set<Value>> phiDef = new LinkedHashMap<>();
    private final Map<BasicBlock, List<BasicBlock>> successors = new LinkedHashMap<>();
    private final Map<BasicBlock, List<BasicBlock>> predecessors = new LinkedHashMap<>();

    public LivenessAnalysis(Function function) {
        this.function = function;
    }

    public void analyze() {
        computeCfg();
        computeDefUse();
        computeLiveInOut();
    }

    private void computeCfg() {
        for (BasicBlock bb : function.getBasicBlocks()) {
            successors.put(bb, new ArrayList<>());
            predecessors.putIfAbsent(bb, new ArrayList<>());
            phiUse.put(bb, new LinkedHashSet<>());
            phiDef.put(bb, new LinkedHashSet<>());
        }

        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = bb.getInstructions();
            if (insts.isEmpty())
                continue;
            Instruction last = insts.get(insts.size() - 1);
            if (last instanceof BrInst br) {
                if (br.getNumOperands() == 3) {
                    addEdge(bb, (BasicBlock)br.getOperand(1));
                    addEdge(bb, (BasicBlock)br.getOperand(2));
                } else {
                    addEdge(bb, (BasicBlock)br.getOperand(0));
                }
            }
        }
    }

    private void addEdge(BasicBlock from, BasicBlock to) {
        successors.get(from).add(to);
        predecessors.get(to).add(from);
    }

    private void computeDefUse() {
        for (BasicBlock bb : function.getBasicBlocks()) {
            Set<Value> bbDef = new LinkedHashSet<>();
            Set<Value> bbUse = new LinkedHashSet<>();
            for (Instruction inst : bb.getInstructions()) {
                // Skip Phi operands - they're handled specially
                if (inst instanceof PhiInst) {
                    // Phi defs
                    if (isAllocatable(inst)) {
                        bbDef.add(inst);
                        phiDef.get(bb).add(inst);
                    }
                    continue;
                }
                // Use
                for (int i = 0; i < inst.getNumOperands(); i++) {
                    if (inst instanceof BrInst br && br.getNumOperands() == 3 && i == 0) {
                        IcmpInst icmp = IcmpBranchFusion.getFusableIcmpFromBr(br);
                        if (icmp != null) {
                            Value lhs = icmp.getOperand(0);
                            if (isAllocatable(lhs) && !bbDef.contains(lhs)) {
                                bbUse.add(lhs);
                            }
                            Value rhs = icmp.getOperand(1);
                            if (isAllocatable(rhs) && !bbDef.contains(rhs)) {
                                bbUse.add(rhs);
                            }
                            continue;
                        }
                    }
                    Value op = inst.getOperand(i);
                    if (isAllocatable(op) && !bbDef.contains(op)) {
                        bbUse.add(op);
                    }
                }
                // Def
                if (isAllocatable(inst)) {
                    bbDef.add(inst);
                }
            }
            def.put(bb, bbDef);
            use.put(bb, bbUse);
            liveIn.put(bb, new LinkedHashSet<>());
            liveOut.put(bb, new LinkedHashSet<>());
        }

        // Compute Phi uses: for each predecessor, collect the values used by Phi in successors
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    Map<BasicBlock, Value> incoming = phi.getIncoming();
                    for (Map.Entry<BasicBlock, Value> entry : incoming.entrySet()) {
                        BasicBlock pred = entry.getKey();
                        Value val = entry.getValue();
                        if (isAllocatable(val) && phiUse.containsKey(pred)) {
                            phiUse.get(pred).add(val);
                        }
                    }
                } else {
                    break; // Phis are always at the beginning
                }
            }
        }
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
        return !(val instanceof Instruction inst) || !(inst.getType() instanceof NoneType);
    }

    private void computeLiveInOut() {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
            Collections.reverse(blocks); // Reverse order for faster convergence

            for (BasicBlock bb : blocks) {
                Set<Value> oldLiveIn = new LinkedHashSet<>(liveIn.get(bb));
                Set<Value> oldLiveOut = new LinkedHashSet<>(liveOut.get(bb));

                Set<Value> newLiveOut = new LinkedHashSet<>();
                for (BasicBlock succ : successors.get(bb)) {
                    Set<Value> succLiveIn = new LinkedHashSet<>(liveIn.get(succ));
                    succLiveIn.removeAll(phiDef.get(succ));
                    newLiveOut.addAll(succLiveIn);
                }
                // Add values needed for Phi nodes at successor edges
                newLiveOut.addAll(phiUse.get(bb));
                liveOut.put(bb, newLiveOut);

                // In[B] = Use[B] Union (Out[B] - Def[B])
                Set<Value> newLiveIn = new LinkedHashSet<>(use.get(bb));
                Set<Value> outMinusDef = new LinkedHashSet<>(newLiveOut);
                outMinusDef.removeAll(def.get(bb));
                newLiveIn.addAll(outMinusDef);
                liveIn.put(bb, newLiveIn);

                if (!newLiveIn.equals(oldLiveIn) || !newLiveOut.equals(oldLiveOut)) {
                    changed = true;
                }
            }
        }
    }

    public Set<Value> getLiveOut(BasicBlock bb) {
        return liveOut.get(bb);
    }

    public Set<Value> getLiveIn(BasicBlock bb) {
        return liveIn.get(bb);
    }
}
