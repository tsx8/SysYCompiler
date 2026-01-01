package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Use;
import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.*;
import top.tsxb.compiler.ir.type.PtrType;
import top.tsxb.compiler.ir.type.ArrType;

import java.util.*;

public class GlobalLocalizationPass implements Pass {
    private static final Set<String> LIB_FUNCS = Set.of(
        "getint", "getch", "getarray", "putint", "putch", "putarray", "putf", "starttime", "stoptime"
    );

    private Map<GlobalVariable, Function> uniqueUserCache;

    @Override
    public boolean run(top.tsxb.compiler.ir.structure.Module module) {
        buildUniqueUserCache(module);
        boolean changed = false;
        for (Function func : module.getFunctionList()) {
            if (func.isDeclaration()) continue;
            changed |= runOnFunction(func);
        }
        return changed;
    }

    private void buildUniqueUserCache(top.tsxb.compiler.ir.structure.Module module) {
        uniqueUserCache = new HashMap<>();
        for (GlobalVariable gv : module.getGlobalList()) {
            Function uniqueFunc = null;
            boolean multiFunc = false;
            for (Use use : gv.getUseList()) {
                if (use.user() instanceof Instruction inst) {
                    if (inst.getParent() == null) continue;
                    Function func = inst.getParent().getParent();
                    if (uniqueFunc == null) {
                        uniqueFunc = func;
                    } else if (uniqueFunc != func) {
                        multiFunc = true;
                        break;
                    }
                } else {
                    multiFunc = true;
                    break;
                }
            }
            if (!multiFunc && uniqueFunc != null) {
                uniqueUserCache.put(gv, uniqueFunc);
            }
        }
    }

    private boolean runOnFunction(Function func) {
        Set<GlobalVariable> worthIt = findWorthItGlobals(func);
        if (worthIt.isEmpty()) return false;

        boolean changed = false;
        for (GlobalVariable gv : worthIt) {
            if (localizeGlobalInFunction(gv, func)) {
                changed = true;
            }
        }
        return changed;
    }

    private Set<GlobalVariable> findWorthItGlobals(Function func) {
        Set<GlobalVariable> globalsInFunc = new HashSet<>();
        Set<GlobalVariable> globalsInLoops = new HashSet<>();
        
        List<Loop> loops = findLoops(func);
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            boolean inLoop = false;
            for (Loop loop : loops) {
                if (loop.blocks.contains(bb)) {
                    inLoop = true;
                    break;
                }
            }
            
            for (Instruction inst : bb.getInstructions()) {
                for (int i = 0; i < inst.getNumOperands(); i++) {
                    Value op = inst.getOperand(i);
                    if (op instanceof GlobalVariable gv) {
                        if (((PtrType) gv.getType()).getPointeeType() instanceof ArrType) continue;
                        globalsInFunc.add(gv);
                        if (inLoop) {
                            globalsInLoops.add(gv);
                        }
                    }
                }
            }
        }
        
        Set<GlobalVariable> result = new HashSet<>();
        for (GlobalVariable gv : globalsInFunc) {
            if (isOnlyUsedIn(gv, func)) {
                result.add(gv);
                continue;
            }
            if (globalsInLoops.contains(gv)) {
                result.add(gv);
            }
        }
        return result;
    }

    private boolean isOnlyUsedIn(GlobalVariable gv, Function func) {
        return uniqueUserCache.get(gv) == func;
    }

    private boolean localizeGlobalInFunction(GlobalVariable gv, Function func) {
        boolean modified = isModifiedIn(gv, func);
        boolean isPrivate = isOnlyUsedIn(gv, func);

        BasicBlock entry = func.getBasicBlocks().get(0);
        AllocaInst alloca = new AllocaInst(((PtrType) gv.getType()).getPointeeType(), gv.getName() + ".local", null);
        entry.addFirst(alloca);
        
        LoadInst entryLoad = new LoadInst(gv, null);
        StoreInst entryStore = new StoreInst(entryLoad, alloca, null);
        
        int idx = entry.getInstructions().indexOf(alloca);
        entry.getInstructions().add(idx + 1, entryLoad);
        entryLoad.setParent(entry);
        entry.getInstructions().add(idx + 2, entryStore);
        entryStore.setParent(entry);

        Set<Instruction> protectedInsts = new HashSet<>();
        protectedInsts.add(entryLoad);
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            ListIterator<Instruction> it = bb.getInstructions().listIterator();
            while (it.hasNext()) {
                Instruction inst = it.next();
                if (protectedInsts.contains(inst)) continue;
                
                if (inst instanceof CallInst call) {
                    if (shouldStoreReloadAroundCall(gv, call, func)) {
                        if (modified) {
                            it.previous();
                            LoadInst sLoad = new LoadInst(alloca, null);
                            sLoad.setParent(bb);
                            it.add(sLoad);
                            StoreInst sStore = new StoreInst(sLoad, gv, null);
                            sStore.setParent(bb);
                            it.add(sStore);
                            protectedInsts.add(sStore);
                            it.next();
                        }
                        
                        if (!isPrivate || modified) {
                            LoadInst rLoad = new LoadInst(gv, null);
                            rLoad.setParent(bb);
                            it.add(rLoad);
                            StoreInst rStore = new StoreInst(rLoad, alloca, null);
                            rStore.setParent(bb);
                            it.add(rStore);
                            protectedInsts.add(rLoad);
                        }
                        continue;
                    }
                }
                
                for (int j = 0; j < inst.getNumOperands(); j++) {
                    if (inst.getOperand(j) == gv) {
                        inst.setOperand(j, alloca);
                    }
                }
            }
            
            if (modified) {
                Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
                if (last instanceof ReturnInst) {
                    ListIterator<Instruction> retIt = bb.getInstructions().listIterator(bb.getInstructions().size() - 1);
                    LoadInst retLoad = new LoadInst(alloca, null);
                    retLoad.setParent(bb);
                    retIt.add(retLoad);
                    StoreInst retStore = new StoreInst(retLoad, gv, null);
                    retStore.setParent(bb);
                    retIt.add(retStore);
                    protectedInsts.add(retStore);
                }
            }
        }
        
        return true;
    }

    private boolean shouldStoreReloadAroundCall(GlobalVariable gv, CallInst call, Function currentFunc) {
        Value callee = call.getOperand(0);
        if (callee instanceof Function f) {
            if (LIB_FUNCS.contains(f.getName())) return false;
            if (isOnlyUsedIn(gv, currentFunc)) {
                return f == currentFunc;
            }
        }
        return true;
    }

    private boolean isModifiedIn(GlobalVariable gv, Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof StoreInst store && store.getOperand(1) == gv) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class Loop {
        BasicBlock header;
        Set<BasicBlock> blocks;
        Loop(BasicBlock header, Set<BasicBlock> blocks) {
            this.header = header;
            this.blocks = blocks;
        }
    }

    private List<Loop> findLoops(Function function) {
        List<Loop> loops = new ArrayList<>();
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);

        for (BasicBlock n : function.getBasicBlocks()) {
            if (!domInfo.dominators().containsKey(n)) continue;
            for (BasicBlock d : cfg.successors().getOrDefault(n, List.of())) {
                if (domInfo.dominators().get(n).contains(d)) {
                    Set<BasicBlock> loopBlocks = findLoopBlocks(n, d, cfg.predecessors());
                    loops.add(new Loop(d, loopBlocks));
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
}
