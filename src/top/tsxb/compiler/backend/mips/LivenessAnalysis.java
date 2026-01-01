package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.constant.Constant;

import java.util.*;

public class LivenessAnalysis {
    private final Function function;
    private final Map<BasicBlock, Set<Value>> liveIn = new HashMap<>();
    private final Map<BasicBlock, Set<Value>> liveOut = new HashMap<>();
    private final Map<BasicBlock, Set<Value>> def = new HashMap<>();
    private final Map<BasicBlock, Set<Value>> use = new HashMap<>();
    private final Map<BasicBlock, List<BasicBlock>> successors = new HashMap<>();
    private final Map<BasicBlock, List<BasicBlock>> predecessors = new HashMap<>();

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
        }

        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = bb.getInstructions();
            if (insts.isEmpty()) continue;
            Instruction last = insts.get(insts.size() - 1);
            if (last instanceof BrInst br) {
                if (br.getNumOperands() == 3) {
                    addEdge(bb, (BasicBlock) br.getOperand(1));
                    addEdge(bb, (BasicBlock) br.getOperand(2));
                } else {
                    addEdge(bb, (BasicBlock) br.getOperand(0));
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
            Set<Value> bbDef = new HashSet<>();
            Set<Value> bbUse = new HashSet<>();
            for (Instruction inst : bb.getInstructions()) {
                // Use
                for (int i = 0; i < inst.getNumOperands(); i++) {
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
            liveIn.put(bb, new HashSet<>());
            liveOut.put(bb, new HashSet<>());
        }
    }

    private boolean isAllocatable(Value val) {
        if (val == null) return false;
        if (val instanceof Constant || val instanceof BasicBlock) return false;
        return !(val instanceof Instruction inst) || !(inst.getType() instanceof NoneType);
    }

    private void computeLiveInOut() {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
            Collections.reverse(blocks); // Reverse order for faster convergence

            for (BasicBlock bb : blocks) {
                Set<Value> oldLiveIn = new HashSet<>(liveIn.get(bb));
                Set<Value> oldLiveOut = new HashSet<>(liveOut.get(bb));

                // Out[B] = Union(In[S] for S in successors(B))
                Set<Value> newLiveOut = new HashSet<>();
                for (BasicBlock succ : successors.get(bb)) {
                    newLiveOut.addAll(liveIn.get(succ));
                }
                liveOut.put(bb, newLiveOut);

                // In[B] = Use[B] Union (Out[B] - Def[B])
                Set<Value> newLiveIn = new HashSet<>(use.get(bb));
                Set<Value> outMinusDef = new HashSet<>(newLiveOut);
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
}
