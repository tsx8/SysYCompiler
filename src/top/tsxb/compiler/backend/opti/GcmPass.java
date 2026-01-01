package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.base.Use;
import top.tsxb.compiler.ir.base.User;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.PhiInst;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.ReturnInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.backend.mips.LoopAnalysis;

import java.util.*;

public class GcmPass implements Pass {
    private DominatorAnalysis.DominatorInfo domInfo;
    private LoopAnalysis loopAnalysis;
    private final Map<Instruction, BasicBlock> earlyBlock = new HashMap<>();
    private final Map<Instruction, BasicBlock> lateBlock = new HashMap<>();
    private final Map<BasicBlock, Integer> domDepth = new HashMap<>();
    private final Set<Instruction> visited = new HashSet<>();

    @Override
    public boolean run(Module module) {
        boolean changed = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration()) continue;
            changed |= runOnFunction(function);
        }
        return changed;
    }

    private void computeDomDepth(BasicBlock root) {
        domDepth.clear();
        Queue<BasicBlock> queue = new LinkedList<>();
        queue.add(root);
        domDepth.put(root, 0);
        while (!queue.isEmpty()) {
            BasicBlock curr = queue.poll();
            for (BasicBlock child : domInfo.domTree().getOrDefault(curr, List.of())) {
                domDepth.put(child, domDepth.get(curr) + 1);
                queue.add(child);
            }
        }
    }

    private boolean runOnFunction(Function function) {
        domInfo = DominatorAnalysis.computeDominators(function);
        loopAnalysis = new LoopAnalysis(function);
        loopAnalysis.analyze();
        computeDomDepth(function.getBasicBlocks().get(0));

        earlyBlock.clear();
        lateBlock.clear();
        visited.clear();

        List<Instruction> allInsts = new ArrayList<>();
        for (BasicBlock bb : function.getBasicBlocks()) {
            allInsts.addAll(bb.getInstructions());
        }

        // Pinned instructions are already in their blocks
        for (Instruction inst : allInsts) {
            if (inst.isPinned()) {
                visited.add(inst);
                earlyBlock.put(inst, inst.getParent());
            }
        }

        // Schedule Early
        BasicBlock entry = function.getBasicBlocks().get(0);
        for (Instruction inst : allInsts) {
            scheduleEarly(inst, entry);
        }

        // Schedule Late
        visited.clear();
        for (Instruction inst : allInsts) {
            if (inst.isPinned()) {
                visited.add(inst);
                lateBlock.put(inst, inst.getParent());
            }
        }
        for (Instruction inst : allInsts) {
            scheduleLate(inst);
        }

        // Move instructions
        boolean changed = false;
        for (Instruction inst : allInsts) {
            if (inst.isPinned()) continue;

            BasicBlock early = earlyBlock.get(inst);
            BasicBlock late = lateBlock.get(inst);
            if (early == null || late == null) continue;

            BasicBlock best = late;
            BasicBlock curr = late;
            while (curr != early) {
                curr = domInfo.idom().get(curr);
                if (loopAnalysis.getLoopDepth(curr) < loopAnalysis.getLoopDepth(best)) {
                    best = curr;
                }
            }

            if (inst.getParent() != best) {
                inst.getParent().getInstructions().remove(inst);
                best.addInstruction(inst);
                changed = true;
            }
        }

        scheduleWithinBlocks(function);

        return changed;
    }

    private void scheduleWithinBlocks(Function function) {
        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = new ArrayList<>(bb.getInstructions());
            if (insts.isEmpty()) continue;

            List<PhiInst> phis = new ArrayList<>();
            Instruction terminator = null;
            List<Instruction> rest = new ArrayList<>();

            for (Instruction inst : insts) {
                if (inst instanceof PhiInst phi) {
                    phis.add(phi);
                } else if (inst instanceof BrInst || inst instanceof ReturnInst) {
                    terminator = inst;
                } else {
                    rest.add(inst);
                }
            }

            bb.getInstructions().clear();
            for (PhiInst phi : phis) {
                bb.addInstruction(phi);
            }

            if (rest.isEmpty()) {
                if (terminator != null) bb.addInstruction(terminator);
                continue;
            }

            Map<Instruction, Integer> inDegree = new HashMap<>();
            Map<Instruction, List<Instruction>> adj = new HashMap<>();
            Map<Instruction, Integer> originalIndex = new HashMap<>();

            for (int i = 0; i < rest.size(); i++) {
                Instruction inst = rest.get(i);
                inDegree.put(inst, 0);
                adj.put(inst, new ArrayList<>());
                originalIndex.put(inst, i);
            }

            Instruction lastPinned = null;
            for (Instruction inst : rest) {
                for (int i = 0; i < inst.getNumOperands(); i++) {
                    Value op = inst.getOperand(i);
                    if (op instanceof Instruction opInst && opInst.getParent() == bb) {
                        if (rest.contains(opInst)) {
                            adj.get(opInst).add(inst);
                            inDegree.put(inst, inDegree.get(inst) + 1);
                        }
                    }
                }
                if (inst.isPinned()) {
                    if (lastPinned != null) {
                        adj.get(lastPinned).add(inst);
                        inDegree.put(inst, inDegree.get(inst) + 1);
                    }
                    lastPinned = inst;
                }
            }

            PriorityQueue<Instruction> ready = new PriorityQueue<>(Comparator.comparingInt(originalIndex::get));
            for (Instruction inst : rest) {
                if (inDegree.get(inst) == 0) {
                    ready.add(inst);
                }
            }

            while (!ready.isEmpty()) {
                Instruction curr = ready.poll();
                bb.addInstruction(curr);
                for (Instruction next : adj.get(curr)) {
                    inDegree.put(next, inDegree.get(next) - 1);
                    if (inDegree.get(next) == 0) {
                        ready.add(next);
                    }
                }
            }

            // Safety for cycles or missed instructions
            if (bb.getInstructions().size() < phis.size() + rest.size()) {
                for (Instruction inst : rest) {
                    if (!bb.getInstructions().contains(inst)) {
                        bb.addInstruction(inst);
                    }
                }
            }

            if (terminator != null) bb.addInstruction(terminator);
        }
    }

    private void scheduleEarly(Instruction inst, BasicBlock root) {
        if (visited.contains(inst)) return;
        visited.add(inst);

        BasicBlock best = root;
        for (int i = 0; i < inst.getNumOperands(); i++) {
            Value op = inst.getOperand(i);
            if (op instanceof Instruction opInst) {
                scheduleEarly(opInst, root);
                BasicBlock opBlock = earlyBlock.get(opInst);
                Integer opDepth = domDepth.get(opBlock);
                Integer bestDepth = domDepth.get(best);
                if (opDepth != null && bestDepth != null && opDepth > bestDepth) {
                    best = opBlock;
                }
            }
        }
        earlyBlock.put(inst, best);
    }

    private void scheduleLate(Instruction inst) {
        if (visited.contains(inst)) return;
        visited.add(inst);

        BasicBlock best = null;
        for (Use use : inst.getUseList()) {
            User user = use.user();
            if (user instanceof Instruction userInst) {
                scheduleLate(userInst);
                BasicBlock useBlock = lateBlock.get(userInst);
                if (userInst instanceof PhiInst phi) {
                    for (Map.Entry<BasicBlock, Value> entry : phi.getIncoming().entrySet()) {
                        if (entry.getValue() == inst) {
                            BasicBlock pred = entry.getKey();
                            best = findLca(best, pred);
                        }
                    }
                } else {
                    best = findLca(best, useBlock);
                }
            }
        }
        if (best == null) {
            best = earlyBlock.get(inst);
        }
        lateBlock.put(inst, best);
    }

    private BasicBlock findLca(BasicBlock b1, BasicBlock b2) {
        if (b1 == null) return b2;
        if (b2 == null) return b1;
        if (b1 == b2) return b1;

        List<BasicBlock> path1 = new ArrayList<>();
        BasicBlock curr = b1;
        while (curr != null) {
            path1.add(curr);
            BasicBlock next = domInfo.idom().get(curr);
            if (next == curr) break;
            curr = next;
        }

        List<BasicBlock> path2 = new ArrayList<>();
        curr = b2;
        while (curr != null) {
            path2.add(curr);
            BasicBlock next = domInfo.idom().get(curr);
            if (next == curr) break;
            curr = next;
        }

        Collections.reverse(path1);
        Collections.reverse(path2);

        BasicBlock lca = path1.get(0);
        for (int i = 1; i < Math.min(path1.size(), path2.size()); i++) {
            if (path1.get(i) == path2.get(i)) {
                lca = path1.get(i);
            } else {
                break;
            }
        }
        return lca;
    }
}
