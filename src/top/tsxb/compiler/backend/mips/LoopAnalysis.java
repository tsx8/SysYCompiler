package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.backend.opti.DominatorAnalysis;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;

import java.util.*;

public class LoopAnalysis {
    private final Function function;
    private final Map<BasicBlock, Integer> loopDepth = new LinkedHashMap<>();

    public LoopAnalysis(Function function) {
        this.function = function;
    }

    public void analyze() {
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);

        for (BasicBlock bb : function.getBasicBlocks()) {
            loopDepth.put(bb, 0);
        }

        // Find back-edges (n -> d where d dominates n)
        for (BasicBlock n : function.getBasicBlocks()) {
            if (!domInfo.dominators().containsKey(n)) continue;
            List<BasicBlock> successors = cfg.successors().getOrDefault(n, List.of());
            for (BasicBlock d : successors) {
                if (domInfo.dominators().get(n).contains(d)) {
                    // Found a back-edge n -> d, d is the loop header
                    Set<BasicBlock> loopNodes = DominatorAnalysis.findLoopBlocks(n, d, cfg.predecessors());
                    for (BasicBlock node : loopNodes) {
                        loopDepth.put(node, loopDepth.get(node) + 1);
                    }
                }
            }
        }
    }

    public int getLoopDepth(BasicBlock bb) {
        return loopDepth.getOrDefault(bb, 0);
    }
}
