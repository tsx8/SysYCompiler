package top.tsxb.compiler.backend.opti;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import top.tsxb.compiler.backend.opti.SccpHelper.LatticeStatus;
import top.tsxb.compiler.backend.opti.SccpHelper.LatticeValue;
import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.Argument;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.IntType;

public class IpsccpPass implements Pass {
    private final Map<Value, LatticeValue> latticeValues = new LinkedHashMap<>();
    private final Map<Function, LatticeValue> returnValues = new LinkedHashMap<>();
    private final Set<BasicBlock> reachableBlocks = new LinkedHashSet<>();
    private final Set<Edge> executableEdges = new LinkedHashSet<>();

    private final Queue<BasicBlock> flowWorklist = new LinkedList<>();
    private final Queue<Instruction> ssaWorklist = new LinkedList<>();

    private final Map<Function, List<CallInst>> callSites = new LinkedHashMap<>();
    private final Set<Function> visiting = new LinkedHashSet<>();
    private final Map<Function, Boolean> purityCache = new LinkedHashMap<>();

    @Override
    public boolean run(Module module) {
        initialize(module);
        solve();
        return rewrite(module);
    }

    private void initialize(Module module) {
        latticeValues.clear();
        returnValues.clear();
        reachableBlocks.clear();
        executableEdges.clear();
        flowWorklist.clear();
        ssaWorklist.clear();
        callSites.clear();
        visiting.clear();
        purityCache.clear();

        for (GlobalVariable gv : module.getGlobalList()) {
            if (gv.isConst() && gv.getNumOperands() > 0 && gv.getOperand(0) instanceof ConstInt ci) {
                latticeValues.put(gv, LatticeValue.constant(ci.getValue()));
            } else if (isReadOnly(gv)) {
                if (gv.getNumOperands() > 0 && gv.getOperand(0) instanceof ConstInt ci) {
                    latticeValues.put(gv, LatticeValue.constant(ci.getValue()));
                } else {
                    latticeValues.put(gv, LatticeValue.constant(0));
                }
            } else {
                latticeValues.put(gv, LatticeValue.bottom());
            }
        }

        Function main = null;
        for (Function f : module.getFunctionList()) {
            if (f.getName().equals("main") || f.getName().equals("@main")) {
                main = f;
                break;
            }
        }
        if (main != null && !main.getBasicBlocks().isEmpty()) {
            flowWorklist.add(main.getBasicBlocks().get(0));
        }

        for (Function func : module.getFunctionList()) {
            returnValues.put(func, LatticeValue.top());
            callSites.put(func, new ArrayList<>());

            for (Argument arg : func.getArguments()) {
                latticeValues.put(arg, LatticeValue.top());
            }

            if (func.getName().equals("main")) {
                for (Argument arg : func.getArguments()) {
                    latticeValues.put(arg, LatticeValue.bottom());
                }
                if (!func.getBasicBlocks().isEmpty()) {
                    flowWorklist.add(func.getBasicBlocks().get(0));
                }
            }
        }
    }

    private void solve() {
        while (!flowWorklist.isEmpty() || !ssaWorklist.isEmpty()) {
            while (!flowWorklist.isEmpty()) {
                BasicBlock bb = flowWorklist.poll();
                if (bb == null)
                    continue;
                if (reachableBlocks.contains(bb))
                    continue;
                reachableBlocks.add(bb);
                for (Instruction inst : bb.getInstructions()) {
                    visitInstruction(inst);
                }
            }

            if (!ssaWorklist.isEmpty()) {
                Instruction inst = ssaWorklist.poll();
                if (reachableBlocks.contains(inst.getParent())) {
                    visitInstruction(inst);
                }
            }
        }
    }

    private LatticeValue getLatticeValue(Value v) {
        if (v instanceof ConstInt ci) {
            return LatticeValue.constant(ci.getValue());
        }
        return latticeValues.getOrDefault(v, LatticeValue.top());
    }

    private void setLatticeValue(Value v, LatticeValue lv) {
        LatticeValue old = getLatticeValue(v);
        if (!old.equals(lv)) {
            latticeValues.put(v, lv);
            for (var use : v.getUseList()) {
                if (use.user() instanceof Instruction inst) {
                    ssaWorklist.add(inst);
                }
            }
        }
    }

    private void visitInstruction(Instruction inst) {
        if (inst instanceof BinaryInst binary)
            visitBinary(binary);
        else if (inst instanceof IcmpInst icmp)
            visitIcmp(icmp);
        else if (inst instanceof PhiInst phi)
            visitPhi(phi);
        else if (inst instanceof BrInst br)
            visitBr(br);
        else if (inst instanceof CallInst call)
            visitCall(call);
        else if (inst instanceof ReturnInst ret)
            visitReturn(ret);
        else if (inst instanceof ZextInst zext)
            visitZext(zext);
        else if (inst instanceof LoadInst load)
            visitLoad(load);
        else {
            setLatticeValue(inst, LatticeValue.bottom());
        }
    }

    private void visitBinary(BinaryInst binary) {
        SccpHelper.visitBinary(binary, this::getLatticeValue, lv -> setLatticeValue(binary, lv));
    }

    private void visitIcmp(IcmpInst icmp) {
        SccpHelper.visitIcmp(icmp, this::getLatticeValue, lv -> setLatticeValue(icmp, lv));
    }

    private void visitZext(ZextInst zext) {
        SccpHelper.visitZext(zext, this::getLatticeValue, lv -> setLatticeValue(zext, lv));
    }

    private void visitLoad(LoadInst load) {
        Value ptr = load.getOperand(0);
        if (ptr instanceof GlobalVariable) {
            LatticeValue val = getLatticeValue(ptr);
            if (val.status() == LatticeStatus.CONSTANT) {
                setLatticeValue(load, val);
                return;
            }
        }
        setLatticeValue(load, LatticeValue.bottom());
    }

    private void visitPhi(PhiInst phi) {
        LatticeValue res = LatticeValue.top();
        boolean hasExecutableEdge = false;
        for (Map.Entry<BasicBlock, Value> entry : phi.getIncoming().entrySet()) {
            BasicBlock incomingBlock = entry.getKey();
            Value val = entry.getValue();

            Edge edge = new Edge(incomingBlock, phi.getParent());
            if (executableEdges.contains(edge)) {
                res = res.meet(getLatticeValue(val));
                hasExecutableEdge = true;
            }
        }
        if (!hasExecutableEdge) {
            setLatticeValue(phi, LatticeValue.top());
        } else {
            setLatticeValue(phi, res);
        }
    }

    private void visitBr(BrInst br) {
        if (br.getNumOperands() == 3) {
            LatticeValue cond = getLatticeValue(br.getOperand(0));
            if (cond.status() == LatticeStatus.CONSTANT) {
                BasicBlock target =
                    cond.value() != 0 ? (BasicBlock)br.getOperand(1) : (BasicBlock)br.getOperand(2);
                markEdgeExecutable(br.getParent(), target);
            } else if (cond.status() == LatticeStatus.BOTTOM) {
                markEdgeExecutable(br.getParent(), (BasicBlock)br.getOperand(1));
                markEdgeExecutable(br.getParent(), (BasicBlock)br.getOperand(2));
            }
        } else {
            markEdgeExecutable(br.getParent(), (BasicBlock)br.getOperand(0));
        }
    }

    private void markEdgeExecutable(BasicBlock from, BasicBlock to) {
        if (from == null || to == null) {
            return;
        }
        Edge edge = new Edge(from, to);
        if (!executableEdges.contains(edge)) {
            executableEdges.add(edge);
            if (!reachableBlocks.contains(to)) {
                flowWorklist.add(to);
            } else {
                for (Instruction inst : to.getInstructions()) {
                    if (inst instanceof PhiInst) {
                        ssaWorklist.add(inst);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private void visitCall(CallInst call) {
        Function func = (Function)call.getOperand(0);

        if (!callSites.get(func).contains(call)) {
            callSites.get(func).add(call);
        }

        if (func.isDeclaration()) {
            setLatticeValue(call, LatticeValue.bottom());
            return;
        }

        for (int i = 0; i < func.getArguments().size(); i++) {
            Argument arg = func.getArguments().get(i);
            Value actualArg = call.getOperand(i + 1);
            LatticeValue argVal = getLatticeValue(actualArg);

            LatticeValue currentArgVal = getLatticeValue(arg);
            LatticeValue newArgVal = currentArgVal.meet(argVal);

            if (!currentArgVal.equals(newArgVal)) {
                latticeValues.put(arg, newArgVal);
                for (var use : arg.getUseList()) {
                    if (use.user() instanceof Instruction inst) {
                        ssaWorklist.add(inst);
                    }
                }
            }
        }

        if (!func.getBasicBlocks().isEmpty()) {
            BasicBlock entry = func.getBasicBlocks().get(0);
            if (!reachableBlocks.contains(entry)) {
                flowWorklist.add(entry);
            }
        }

        if (isPure(func)) {
            List<Integer> constantArgs = new ArrayList<>();
            boolean allConstants = true;
            for (int i = 0; i < func.getArguments().size(); i++) {
                LatticeValue val = getLatticeValue(call.getOperand(i + 1));
                if (val.status() == LatticeStatus.CONSTANT) {
                    constantArgs.add(val.value());
                } else {
                    allConstants = false;
                    break;
                }
            }

            if (allConstants) {
                Integer res = evaluatePureFunction(func, constantArgs);
                if (res != null) {
                    setLatticeValue(call, LatticeValue.constant(res));
                    return;
                }
            }
        }

        LatticeValue retVal = returnValues.get(func);
        setLatticeValue(call, retVal);
    }

    private void visitReturn(ReturnInst ret) {
        Function func = ret.getParent().getParent();
        LatticeValue currentRet = returnValues.get(func);

        LatticeValue newRet;
        if (ret.getNumOperands() > 0) {
            newRet = currentRet.meet(getLatticeValue(ret.getOperand(0)));
        } else {
            newRet = LatticeValue.bottom();
        }

        if (!currentRet.equals(newRet)) {
            returnValues.put(func, newRet);
            ssaWorklist.addAll(callSites.get(func));
        }
    }

    private boolean isPure(Function func) {
        if (purityCache.containsKey(func))
            return purityCache.get(func);
        if (visiting.contains(func))
            return true;

        visiting.add(func);
        boolean pure = true;

        outer:
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof StoreInst) {
                    pure = false;
                    break outer;
                }
                if (inst instanceof CallInst call) {
                    Function callee = (Function)call.getOperand(0);
                    if (callee.isDeclaration()) {
                        pure = false;
                        break outer;
                    }
                    if (!isPure(callee)) {
                        pure = false;
                        break outer;
                    }
                }
                if (inst instanceof LoadInst) {
                    pure = false;
                    break outer;
                }
            }
        }

        visiting.remove(func);
        purityCache.put(func, pure);
        return pure;
    }

    private Integer evaluatePureFunction(Function func, List<Integer> args) {
        Map<Value, Integer> context = new LinkedHashMap<>();
        for (int i = 0; i < func.getArguments().size(); i++) {
            context.put(func.getArguments().get(i), args.get(i));
        }

        int steps = 0;
        int maxSteps = 1000;

        BasicBlock currentBlock = func.getBasicBlocks().get(0);
        BasicBlock prevBlock = null;

        while (steps++ < maxSteps) {
            for (Instruction inst : currentBlock.getInstructions()) {
                if (inst instanceof ReturnInst ret) {
                    if (ret.getNumOperands() > 0) {
                        Value retVal = ret.getOperand(0);
                        if (retVal instanceof ConstInt ci)
                            return ci.getValue();
                        return context.get(retVal);
                    }
                    return 0;
                }

                if (inst instanceof BrInst br) {
                    prevBlock = currentBlock;
                    if (br.getNumOperands() == 3) {
                        Integer cond = getVal(br.getOperand(0), context);
                        if (cond == null)
                            return null;
                        currentBlock = cond != 0 ? (BasicBlock)br.getOperand(1) : (BasicBlock)br.getOperand(2);
                    } else {
                        currentBlock = (BasicBlock)br.getOperand(0);
                    }
                    break;
                }

                if (inst instanceof BinaryInst bin) {
                    Integer v1 = getVal(bin.getOperand(0), context);
                    Integer v2 = getVal(bin.getOperand(1), context);
                    if (v1 == null || v2 == null)
                        return null;
                    ConstInt res = ConstantFolder.foldBinary(bin.getOpCode(), new ConstInt(IntType.I32, v1),
                        new ConstInt(IntType.I32, v2));
                    if (res == null)
                        return null;
                    context.put(bin, res.getValue());
                } else if (inst instanceof IcmpInst icmp) {
                    Integer v1 = getVal(icmp.getOperand(0), context);
                    Integer v2 = getVal(icmp.getOperand(1), context);
                    if (v1 == null || v2 == null)
                        return null;
                    ConstInt res = ConstantFolder.foldIcmp(icmp.getPredicate(), new ConstInt(IntType.I32, v1),
                        new ConstInt(IntType.I32, v2));
                    if (res == null)
                        return null;
                    context.put(icmp, res.getValue());
                } else if (inst instanceof PhiInst phi) {
                    if (prevBlock == null)
                        return null;
                    Value val = phi.getIncomingValue(prevBlock);
                    if (val == null)
                        return null;
                    Integer v = getVal(val, context);
                    if (v == null)
                        return null;
                    context.put(phi, v);
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private Integer getVal(Value v, Map<Value, Integer> context) {
        if (v instanceof ConstInt ci)
            return ci.getValue();
        return context.get(v);
    }

    private boolean rewrite(Module module) {
        boolean changed = false;
        for (Function func : module.getFunctionList()) {
            if (func.isDeclaration())
                continue;

            List<BasicBlock> blocksToRemove = new ArrayList<>();
            for (BasicBlock bb : func.getBasicBlocks()) {
                if (!reachableBlocks.contains(bb)) {
                    blocksToRemove.add(bb);
                }
            }

            for (BasicBlock bb : blocksToRemove) {
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof BrInst br) {
                        for (int i = 0; i < br.getNumOperands(); i++) {
                            if (br.getOperand(i) instanceof BasicBlock target) {
                                for (Instruction targetInst : target.getInstructions()) {
                                    if (targetInst instanceof PhiInst phi) {
                                        phi.removeIncoming(bb);
                                    }
                                }
                            }
                        }
                    }
                }
                for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                    inst.dropAllReferences();
                }
                func.getBasicBlocks().remove(bb);
                changed = true;
            }

            for (Argument arg : func.getArguments()) {
                LatticeValue lv = getLatticeValue(arg);
                if (lv.status() == LatticeStatus.CONSTANT) {
                    if (arg.getType() instanceof IntType intType) {
                        arg.replaceAllUsesWith(new ConstInt(intType, lv.value()));
                        changed = true;
                    }
                }
            }

            for (BasicBlock bb : func.getBasicBlocks()) {
                List<Instruction> insts = new ArrayList<>(bb.getInstructions());
                for (Instruction inst : insts) {
                    LatticeValue lv = getLatticeValue(inst);
                    if (lv.status() == LatticeStatus.CONSTANT) {
                        if (!(inst instanceof BrInst) && !(inst instanceof ReturnInst)) {
                            if (inst.getType() instanceof IntType intType) {
                                inst.replaceAllUsesWith(new ConstInt(intType, lv.value()));
                            }
                            boolean canRemove = true;
                            if (inst instanceof CallInst call) {
                                Function callee = (Function)call.getOperand(0);
                                if (!isPure(callee)) {
                                    canRemove = false;
                                }
                            }
                            if (canRemove) {
                                inst.dropAllReferences();
                                bb.getInstructions().remove(inst);
                            }
                            changed = true;
                        }
                    }
                }
            }

            for (BasicBlock bb : func.getBasicBlocks()) {
                if (bb.getInstructions().isEmpty())
                    continue;
                Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
                if (last instanceof BrInst br && br.getNumOperands() == 3) {
                    LatticeValue cond = getLatticeValue(br.getOperand(0));
                    if (cond.status() == LatticeStatus.CONSTANT) {
                        BasicBlock target =
                            cond.value() != 0 ? (BasicBlock)br.getOperand(1) : (BasicBlock)br.getOperand(2);
                        BasicBlock notTaken =
                            cond.value() != 0 ? (BasicBlock)br.getOperand(2) : (BasicBlock)br.getOperand(1);

                        if (target != notTaken) {
                            for (Instruction i : notTaken.getInstructions()) {
                                if (i instanceof PhiInst phi) {
                                    phi.removeIncoming(bb);
                                } else {
                                    break;
                                }
                            }
                        }

                        br.dropAllReferences();
                        bb.getInstructions().remove(br);
                        new BrInst(target, bb);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean isReadOnly(GlobalVariable gv) {
        for (var use : gv.getUseList()) {
            if (use.user() instanceof StoreInst store) {
                if (store.getOperand(1) == gv)
                    return false;
            }
            if (use.user() instanceof CallInst)
                return false;
        }
        return true;
    }

    private record Edge(BasicBlock from, BasicBlock to) {
    }
}
