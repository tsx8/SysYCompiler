package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import top.tsxb.compiler.ir.base.Use;
import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.AllocaInst;
import top.tsxb.compiler.ir.inst.CallInst;
import top.tsxb.compiler.ir.inst.GetElementPtrInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.LoadInst;
import top.tsxb.compiler.ir.inst.ReturnInst;
import top.tsxb.compiler.ir.inst.StoreInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.type.ArrType;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.PtrType;

public class GlobalLocalizationPass implements Pass {

    private final Set<String> localizedInFunc = new LinkedHashSet<>();
    private Map<GlobalVariable, Function> uniqueUserCache;
    private SideEffectAnalysis sea;

    private record ConstIndexKey(List<Integer> indices) {
    }

    private static GlobalVariable getGlobalVariable(Value v) {
        if (v instanceof GlobalVariable gv)
            return gv;
        if (v instanceof GetElementPtrInst gep) {
            return getGlobalVariable(gep.getOperand(0));
        }
        return null;
    }

    @Override
    public boolean run(top.tsxb.compiler.ir.structure.Module module) {
        localizedInFunc.clear();
        buildUniqueUserCache(module);
        sea = new SideEffectAnalysis();
        sea.analyze(module);
        boolean changed = false;
        for (Function func : module.getFunctionList()) {
            if (func.isDeclaration())
                continue;
            changed |= runOnFunction(func);
        }
        return changed;
    }

    private void buildUniqueUserCache(top.tsxb.compiler.ir.structure.Module module) {
        uniqueUserCache = new LinkedHashMap<>();
        for (GlobalVariable gv : module.getGlobalList()) {
            Function uniqueFunc = null;
            boolean multiFunc = false;
            for (Use use : gv.getUseList()) {
                if (use.user() instanceof Instruction inst) {
                    if (inst.getParent() == null)
                        continue;
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
        if (worthIt.isEmpty())
            return false;

        boolean changed = false;
        for (GlobalVariable gv : worthIt) {
            String key = func.getName() + ":" + gv.getName();
            if (localizedInFunc.contains(key))
                continue;

            if (localizeGlobalInFunction(gv, func)) {
                localizedInFunc.add(key);
                changed = true;
            }
        }
        return changed;
    }

    private Set<GlobalVariable> findWorthItGlobals(Function func) {
        Set<GlobalVariable> globalsInFunc = new LinkedHashSet<>();
        Set<GlobalVariable> globalsInLoops = new LinkedHashSet<>();

        List<Loop> loops = findLoops(func);
        boolean isRecursive = isRecursive(func);

        for (BasicBlock bb : func.getBasicBlocks()) {
            boolean inLoop = false;
            for (Loop loop : loops) {
                if (loop.blocks().contains(bb)) {
                    inLoop = true;
                    break;
                }
            }

            for (Instruction inst : bb.getInstructions()) {
                for (int i = 0; i < inst.getNumOperands(); i++) {
                    Value op = inst.getOperand(i);
                    if (op instanceof GlobalVariable gv && !gv.isConst()) {
                        globalsInFunc.add(gv);
                        if (inLoop) {
                            globalsInLoops.add(gv);
                        }
                    }
                }
            }
        }

        Set<GlobalVariable> result = new LinkedHashSet<>();
        for (GlobalVariable gv : globalsInFunc) {
            if (isOnlyUsedIn(gv, func)) {
                // If it's recursive and modified, localization might be bad due to syncs
                if (isRecursive && isModifiedIn(gv, func)) {
                    continue;
                }
                result.add(gv);
                continue;
            }
            if (globalsInLoops.contains(gv)) {
                // If it's recursive and modified, localization might be bad due to syncs
                if (isRecursive && isModifiedIn(gv, func)) {
                    continue;
                }
                result.add(gv);
            }
        }
        return result;
    }

    private boolean isRecursive(Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst call && call.getOperand(0) == func) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOnlyUsedIn(GlobalVariable gv, Function func) {
        return uniqueUserCache.get(gv) == func;
    }

    private boolean localizeGlobalInFunction(GlobalVariable gv, Function func) {
        if (((PtrType)gv.getType()).getPointeeType() instanceof ArrType) {
            return localizeArrayInFunction(gv, func);
        }
        String key = gv.getName() + "@" + func.getName();
        if (localizedInFunc.contains(key))
            return false;
        if (isScalarLocalized(gv, func)) {
            localizedInFunc.add(key);
            return false;
        }
        boolean modified = isModifiedIn(gv, func);
        isOnlyUsedIn(gv, func);

        BasicBlock entry = func.getBasicBlocks().get(0);
        AllocaInst alloca = new AllocaInst(((PtrType)gv.getType()).getPointeeType(), gv.getName() + ".local", null);
        entry.addFirst(alloca);

        LoadInst entryLoad = new LoadInst(gv, null);
        StoreInst entryStore = new StoreInst(entryLoad, alloca, null);

        int idx = entry.getInstructions().indexOf(alloca);
        entry.getInstructions().add(idx + 1, entryLoad);
        entryLoad.setParent(entry);
        func.resolveLocalName(entryLoad);
        entry.getInstructions().add(idx + 2, entryStore);
        entryStore.setParent(entry);

        Set<Instruction> protectedInsts = new LinkedHashSet<>();
        protectedInsts.add(entryLoad);

        for (BasicBlock bb : func.getBasicBlocks()) {
            ListIterator<Instruction> it = bb.getInstructions().listIterator();
            while (it.hasNext()) {
                Instruction inst = it.next();
                if (protectedInsts.contains(inst))
                    continue;

                if (inst instanceof CallInst call) {
                    boolean reloadNeeded = needsReload(gv, call);
                    boolean storeNeeded = modified && (needsStore(gv, call) || reloadNeeded);

                    if (storeNeeded) {
                        it.previous();
                        storeBackToScalar(gv, alloca, bb, it, func, protectedInsts);
                        it.next();
                    }

                    if (reloadNeeded) {
                        reloadFromScalar(gv, alloca, bb, it, func, protectedInsts);
                    }
                    if (storeNeeded || reloadNeeded)
                        continue;
                }

                for (int j = 0; j < inst.getNumOperands(); j++) {
                    if (inst.getOperand(j) == gv) {
                        inst.setOperand(j, alloca);
                    }
                }
            }

            if (modified) {
                if (bb.getInstructions().isEmpty())
                    continue;
                Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
                if (last instanceof ReturnInst) {
                    ListIterator<Instruction> retIt =
                        bb.getInstructions().listIterator(bb.getInstructions().size() - 1);
                    storeBackToScalar(gv, alloca, bb, retIt, func, protectedInsts);
                }
            }
        }

        localizedInFunc.add(key);
        return true;
    }

    private void storeBackToScalar(GlobalVariable gv, AllocaInst alloca, BasicBlock bb, ListIterator<Instruction> it,
        Function func, Set<Instruction> protectedInsts) {
        LoadInst load = new LoadInst(alloca, null);
        load.setParent(bb);
        it.add(load);
        func.resolveLocalName(load);
        StoreInst store = new StoreInst(load, gv, null);
        store.setParent(bb);
        it.add(store);
        protectedInsts.add(store);
    }

    private void reloadFromScalar(GlobalVariable gv, AllocaInst alloca, BasicBlock bb, ListIterator<Instruction> it,
        Function func, Set<Instruction> protectedInsts) {
        LoadInst load = new LoadInst(gv, null);
        load.setParent(bb);
        it.add(load);
        func.resolveLocalName(load);
        StoreInst store = new StoreInst(load, alloca, null);
        store.setParent(bb);
        it.add(store);
        protectedInsts.add(load);
    }

    private boolean isScalarLocalized(GlobalVariable gv, Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca && alloca.getName().startsWith(gv.getName() + ".local")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBaseHoisted(GlobalVariable gv, Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof GetElementPtrInst gep && gep.getOperand(0) == gv) {
                    if (gep.getNumOperands() == 3 && isZero(gep.getOperand(1)) && isZero(gep.getOperand(2))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isZero(Value v) {
        return v instanceof ConstInt ci && ci.getValue() == 0;
    }

    private boolean localizeArrayInFunction(GlobalVariable gv, Function func) {
        String key = gv.getName() + "@" + func.getName();
        if (localizedInFunc.contains(key))
            return false;
        if (isBaseHoisted(gv, func)) {
            localizedInFunc.add(key);
            return false;
        }
        List<GetElementPtrInst> geps = new ArrayList<>();
        List<Instruction> otherUses = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                for (int i = 0; i < inst.getNumOperands(); i++) {
                    if (inst.getOperand(i) == gv) {
                        if (inst instanceof GetElementPtrInst gep) {
                            geps.add(gep);
                        } else {
                            otherUses.add(inst);
                        }
                    }
                }
            }
        }
        if (geps.isEmpty() && otherUses.isEmpty())
            return false;

        boolean allConstant = otherUses.isEmpty();
        Set<ConstIndexKey> uniqueConstantIndices = new LinkedHashSet<>();
        int constantCount = 0;
        for (GetElementPtrInst gep : geps) {
            ConstIndexKey indices = getConstIndexKey(gep);
            if (indices != null) {
                uniqueConstantIndices.add(indices);
                constantCount++;
            } else {
                allConstant = false;
            }
        }

        double constantRatio = geps.isEmpty() ? 0 : (double)constantCount / geps.size();
        boolean result;
        if (allConstant && constantRatio >= 0.01) {
            result = localizeArrayElements(gv, func, uniqueConstantIndices);
        } else {
            result = hoistArrayBase(gv, func, geps);
        }
        if (result) {
            localizedInFunc.add(key);
        }
        return result;
    }

    private ConstIndexKey getConstIndexKey(GetElementPtrInst gep) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 1; i < gep.getNumOperands(); i++) {
            Value idx = gep.getOperand(i);
            if (!(idx instanceof ConstInt ci)) {
                return null;
            }
            indices.add(ci.getValue());
        }
        return new ConstIndexKey(List.copyOf(indices));
    }

    private List<Value> buildConstIndices(ConstIndexKey key) {
        List<Value> indices = new ArrayList<>(key.indices().size());
        for (Integer idx : key.indices()) {
            indices.add(new ConstInt(IntType.I32, idx));
        }
        return indices;
    }

    private AllocaInst findArrayElementAlloca(GlobalVariable gv, Function func, ConstIndexKey key) {
        String targetName = gv.getName() + "." + indicesToString(buildConstIndices(key));
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca && alloca.getName().startsWith(targetName)) {
                    return alloca;
                }
            }
        }
        return null;
    }

    private boolean localizeArrayElements(GlobalVariable gv, Function func, Set<ConstIndexKey> constantIndices) {
        Map<ConstIndexKey, AllocaInst> indexToAlloca = new LinkedHashMap<>();
        BasicBlock entry = func.getBasicBlocks().get(0);
        boolean modified = isModifiedIn(gv, func);
        isOnlyUsedIn(gv, func);
        Set<Instruction> protectedInsts = new LinkedHashSet<>();

        for (ConstIndexKey key : constantIndices) {
            AllocaInst existing = findArrayElementAlloca(gv, func, key);
            if (existing != null) {
                indexToAlloca.put(key, existing);
                continue;
            }

            List<Value> indices = buildConstIndices(key);
            GetElementPtrInst tempGep = new GetElementPtrInst(gv, indices, null);
            IrType elementType = ((PtrType)tempGep.getType()).getPointeeType();
            String name = gv.getName() + "." + indicesToString(indices);
            AllocaInst alloca = new AllocaInst(elementType, name, null);
            entry.addFirst(alloca);
            indexToAlloca.put(key, alloca);

            LoadInst load = new LoadInst(tempGep, null);
            StoreInst store = new StoreInst(load, alloca, null);
            int idx = entry.getInstructions().indexOf(alloca);
            entry.getInstructions().add(idx + 1, tempGep);
            tempGep.setParent(entry);
            func.resolveLocalName(tempGep);
            entry.getInstructions().add(idx + 2, load);
            load.setParent(entry);
            func.resolveLocalName(load);
            entry.getInstructions().add(idx + 3, store);
            store.setParent(entry);
            protectedInsts.add(tempGep);
            protectedInsts.add(load);
            protectedInsts.add(store);
        }
        if (indexToAlloca.isEmpty())
            return false;

        for (BasicBlock bb : func.getBasicBlocks()) {
            ListIterator<Instruction> it = bb.getInstructions().listIterator();
            while (it.hasNext()) {
                Instruction inst = it.next();
                if (protectedInsts.contains(inst))
                    continue;
                if (inst instanceof GetElementPtrInst gep && gep.getOperand(0) == gv) {
                    ConstIndexKey indices = getConstIndexKey(gep);
                    AllocaInst alloca = indices == null ? null : indexToAlloca.get(indices);
                    if (alloca != null) {
                        gep.replaceAllUsesWith(alloca);
                        it.remove();
                        continue;
                    }
                }

                if (inst instanceof CallInst call) {
                    boolean reloadNeeded = needsReload(gv, call);
                    boolean storeNeeded = modified && (needsStore(gv, call) || reloadNeeded);

                    if (storeNeeded || reloadNeeded) {
                        for (Map.Entry<ConstIndexKey, AllocaInst> entry_ : indexToAlloca.entrySet()) {
                            ConstIndexKey indices = entry_.getKey();
                            AllocaInst alloca = entry_.getValue();
                            if (storeNeeded) {
                                it.previous();
                                storeBackToArray(gv, indices, alloca, bb, it, func, protectedInsts);
                                it.next();
                            }
                            if (reloadNeeded) {
                                reloadFromArray(gv, indices, alloca, bb, it, func, protectedInsts);
                            }
                        }
                    }
                }
            }

            if (modified) {
                if (bb.getInstructions().isEmpty())
                    continue;
                Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
                if (last instanceof ReturnInst) {
                    ListIterator<Instruction> retIt =
                        bb.getInstructions().listIterator(bb.getInstructions().size() - 1);
                    for (Map.Entry<ConstIndexKey, AllocaInst> entry_ : indexToAlloca.entrySet()) {
                        ConstIndexKey indices = entry_.getKey();
                        AllocaInst alloca = entry_.getValue();
                        storeBackToArray(gv, indices, alloca, bb, retIt, func, protectedInsts);
                    }
                }
            }
        }
        return true;
    }

    private void storeBackToArray(GlobalVariable gv, ConstIndexKey indices, AllocaInst alloca, BasicBlock bb,
        ListIterator<Instruction> it, Function func, Set<Instruction> protectedInsts) {
        LoadInst load = new LoadInst(alloca, null);
        load.setParent(bb);
        it.add(load);
        func.resolveLocalName(load);
        GetElementPtrInst gep = new GetElementPtrInst(gv, buildConstIndices(indices), null);
        gep.setParent(bb);
        it.add(gep);
        func.resolveLocalName(gep);
        StoreInst store = new StoreInst(load, gep, null);
        store.setParent(bb);
        it.add(store);
        protectedInsts.add(gep);
        protectedInsts.add(store);
    }

    private void reloadFromArray(GlobalVariable gv, ConstIndexKey indices, AllocaInst alloca, BasicBlock bb,
        ListIterator<Instruction> it, Function func, Set<Instruction> protectedInsts) {
        GetElementPtrInst gep = new GetElementPtrInst(gv, buildConstIndices(indices), null);
        gep.setParent(bb);
        it.add(gep);
        func.resolveLocalName(gep);
        LoadInst load = new LoadInst(gep, null);
        load.setParent(bb);
        it.add(load);
        func.resolveLocalName(load);
        StoreInst store = new StoreInst(load, alloca, null);
        store.setParent(bb);
        it.add(store);
        protectedInsts.add(gep);
        protectedInsts.add(load);
    }

    private boolean hoistArrayBase(GlobalVariable gv, Function func, List<GetElementPtrInst> geps) {
        if (isBaseHoisted(gv, func))
            return false;
        BasicBlock entry = func.getBasicBlocks().get(0);
        List<Value> baseIndices = List.of(new ConstInt(IntType.I32, 0), new ConstInt(IntType.I32, 0));
        GetElementPtrInst baseGep = new GetElementPtrInst(gv, baseIndices, null);
        int insertIdx = 0;
        while (insertIdx < entry.getInstructions().size()
            && entry.getInstructions().get(insertIdx) instanceof AllocaInst) {
            insertIdx++;
        }
        entry.getInstructions().add(insertIdx, baseGep);
        baseGep.setParent(entry);
        func.resolveLocalName(baseGep);

        boolean changed = false;
        for (GetElementPtrInst gep : geps) {
            if (gep.getParent() == null || gep == baseGep)
                continue;
            List<Value> newIndices = new ArrayList<>();
            for (int i = 2; i < gep.getNumOperands(); i++) {
                newIndices.add(gep.getOperand(i));
            }
            GetElementPtrInst newGep = new GetElementPtrInst(baseGep, newIndices, null);
            newGep.setParent(gep.getParent());
            func.resolveLocalName(newGep);
            gep.replaceAllUsesWith(newGep);
            int idx = gep.getParent().getInstructions().indexOf(gep);
            gep.getParent().getInstructions().set(idx, newGep);
            changed = true;
        }
        return changed;
    }

    private String indicesToString(List<Value> indices) {
        StringBuilder sb = new StringBuilder();
        for (Value v : indices) {
            if (v instanceof ConstInt ci) {
                sb.append(ci.getValue()).append("_");
            } else {
                sb.append("x_");
            }
        }
        return sb.toString();
    }

    private boolean needsStore(GlobalVariable gv, CallInst call) {
        Value callee = call.getOperand(0);
        if (callee instanceof Function f) {
            for (int i = 1; i < call.getNumOperands(); i++) {
                if (getGlobalVariable(call.getOperand(i)) == gv)
                    return true;
            }
            if (f.isDeclaration()) {
                return f.getName().equals("putarray");
            }
            return sea.references(f, gv);
        }
        return true;
    }

    private boolean needsReload(GlobalVariable gv, CallInst call) {
        Value callee = call.getOperand(0);
        if (callee instanceof Function f) {
            for (int i = 1; i < call.getNumOperands(); i++) {
                if (getGlobalVariable(call.getOperand(i)) == gv)
                    return true;
            }
            if (f.isDeclaration()) {
                return f.getName().equals("getarray");
            }
            return sea.modifies(f, gv);
        }
        return true;
    }

    private boolean isModifiedIn(GlobalVariable gv, Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof StoreInst store) {
                    Value ptr = store.getOperand(1);
                    if (isDerivedFrom(ptr, gv))
                        return true;
                }
            }
        }
        return false;
    }

    private boolean isDerivedFrom(Value ptr, GlobalVariable gv) {
        if (ptr == gv)
            return true;
        if (ptr instanceof GetElementPtrInst gep) {
            return isDerivedFrom(gep.getOperand(0), gv);
        }
        return false;
    }

    private List<Loop> findLoops(Function function) {
        List<Loop> loops = new ArrayList<>();
        DominatorAnalysis.Cfg cfg = DominatorAnalysis.computeCfg(function);
        DominatorAnalysis.DominatorInfo domInfo = DominatorAnalysis.computeDominators(function);

        for (BasicBlock n : function.getBasicBlocks()) {
            if (!domInfo.dominators().containsKey(n))
                continue;
            for (BasicBlock d : cfg.successors().getOrDefault(n, List.of())) {
                if (domInfo.dominators().get(n).contains(d)) {
                    Set<BasicBlock> loopBlocks = DominatorAnalysis.findLoopBlocks(n, d, cfg.predecessors());
                    loops.add(new Loop(d, loopBlocks));
                }
            }
        }
        return loops;
    }

    private static class SideEffectAnalysis {
        private final Map<Function, Set<GlobalVariable>> modSet = new LinkedHashMap<>();
        private final Map<Function, Set<GlobalVariable>> refSet = new LinkedHashMap<>();
        private final Map<Function, Set<Function>> callGraph = new LinkedHashMap<>();

        public void analyze(top.tsxb.compiler.ir.structure.Module module) {
            for (Function func : module.getFunctionList()) {
                modSet.put(func, new LinkedHashSet<>());
                refSet.put(func, new LinkedHashSet<>());
                callGraph.put(func, new LinkedHashSet<>());

                if (func.isDeclaration())
                    continue;

                for (BasicBlock bb : func.getBasicBlocks()) {
                    for (Instruction inst : bb.getInstructions()) {
                        if (inst instanceof LoadInst load) {
                            GlobalVariable gv = getGlobalVariable(load.getOperand(0));
                            if (gv != null)
                                refSet.get(func).add(gv);
                        } else if (inst instanceof StoreInst store) {
                            GlobalVariable gv = getGlobalVariable(store.getOperand(1));
                            if (gv != null)
                                modSet.get(func).add(gv);
                        } else if (inst instanceof CallInst call) {
                            Value callee = call.getOperand(0);
                            if (callee instanceof Function f) {
                                callGraph.get(func).add(f);
                                // If we pass a pointer to a global variable, assume it's modified/referenced
                                for (int i = 1; i < call.getNumOperands(); i++) {
                                    GlobalVariable gv = getGlobalVariable(call.getOperand(i));
                                    if (gv != null) {
                                        modSet.get(func).add(gv);
                                        refSet.get(func).add(gv);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            boolean changed = true;
            while (changed) {
                changed = false;
                for (Function func : module.getFunctionList()) {
                    Set<GlobalVariable> funcMod = modSet.get(func);
                    Set<GlobalVariable> funcRef = refSet.get(func);
                    for (Function callee : callGraph.get(func)) {
                        if (funcMod.addAll(modSet.get(callee)))
                            changed = true;
                        if (funcRef.addAll(refSet.get(callee)))
                            changed = true;
                    }
                }
            }
        }

        public boolean modifies(Function func, GlobalVariable gv) {
            return modSet.getOrDefault(func, Collections.emptySet()).contains(gv);
        }

        public boolean references(Function func, GlobalVariable gv) {
            return refSet.getOrDefault(func, Collections.emptySet()).contains(gv);
        }
    }

    private record Loop(BasicBlock header, Set<BasicBlock> blocks) {
    }
}
