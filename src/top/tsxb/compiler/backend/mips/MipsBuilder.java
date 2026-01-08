package top.tsxb.compiler.backend.mips;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import top.tsxb.compiler.ir.base.Use;
import top.tsxb.compiler.ir.base.User;
import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstArray;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.constant.ConstString;
import top.tsxb.compiler.ir.constant.ConstZero;
import top.tsxb.compiler.ir.constant.Constant;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.Argument;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalValue;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.ArrType;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.type.PtrType;

public class MipsBuilder {
    private final Module module;
    private final StringBuilder sb = new StringBuilder();
    private final StringBuilder pendingBridges = new StringBuilder();
    private final Map<Value, Integer> stackOffsets = new LinkedHashMap<>();
    private final Map<GlobalValue, String> globalAddrCache = new LinkedHashMap<>();
    private final Map<String, GlobalValue> regToGlobal = new LinkedHashMap<>();
    private final Map<BasicBlock, List<BasicBlock>> predecessorsMap = new LinkedHashMap<>();
    private StringBuilder currentSb = sb;
    private Map<Value, MipsRegister> regMapping = new LinkedHashMap<>();
    private Map<Value, LiveInterval> intervals = new LinkedHashMap<>();
    private Map<Instruction, Integer> instToId = new LinkedHashMap<>();
    private Set<MipsRegister> usedCalleeSaved = new LinkedHashSet<>();
    private int currentStackSize;
    private int brCounter = 0;
    private boolean isLeaf = false;
    private int spShift = 0;
    private Function currentFunction;

    public MipsBuilder(Module module) {
        this.module = module;
    }

    public String build() {
        genData();
        genText();
        return sb.toString();
    }

    private void addI(String dest, String src, int imm) {
        invalidateCache(dest);
        if (imm >= -32768 && imm <= 32767) {
            currentSb.append("    addiu ").append(dest).append(", ").append(src).append(", ").append(imm).append("\n");
        } else {
            invalidateCache("$at");
            // Load immediate to $at (assembler temporary) or $t9
            currentSb.append("    li $at, ").append(imm).append("\n");
            currentSb.append("    addu ").append(dest).append(", ").append(src).append(", $at\n");
        }
    }

    private void loadStack(String dest, int offset) {
        invalidateCache(dest);
        if (offset >= -32768 && offset <= 32767) {
            currentSb.append("    lw ").append(dest).append(", ").append(offset).append("($sp)\n");
        } else {
            invalidateCache("$at");
            currentSb.append("    li $at, ").append(offset).append("\n");
            currentSb.append("    addu $at, $sp, $at\n");
            currentSb.append("    lw ").append(dest).append(", 0($at)\n");
        }
    }

    private void storeStack(String src, int offset) {
        if (offset >= -32768 && offset <= 32767) {
            currentSb.append("    sw ").append(src).append(", ").append(offset).append("($sp)\n");
        } else {
            currentSb.append("    li $at, ").append(offset).append("\n");
            currentSb.append("    addu $at, $sp, $at\n");
            currentSb.append("    sw ").append(src).append(", 0($at)\n");
        }
    }

    private String getLabel(Value val) {
        if (val instanceof BasicBlock bb) {
            return bb.getParent().getName() + "_" + bb.getName().replace(".", "_");
        }
        return val.getName().replace(".", "_");
    }

    private void invalidateCache(String... regs) {
        for (String reg : regs) {
            GlobalValue gv = regToGlobal.remove(reg);
            if (gv != null) {
                globalAddrCache.remove(gv);
            }
        }
    }

    private void updateCache(GlobalValue gv, String reg) {
        invalidateCache(reg);
        String oldReg = globalAddrCache.remove(gv);
        if (oldReg != null) {
            regToGlobal.remove(oldReg);
        }
        globalAddrCache.put(gv, reg);
        regToGlobal.put(reg, gv);
    }

    private void invalidateCallerSaved() {
        List<String> toRemove = new ArrayList<>();
        for (String reg : regToGlobal.keySet()) {
            if (reg.startsWith("$t") || reg.startsWith("$a") || reg.startsWith("$v") || reg.equals("$at")) {
                toRemove.add(reg);
            }
        }
        for (String reg : toRemove) {
            invalidateCache(reg);
        }
    }

    private void genData() {
        currentSb.append(".data\n");
        for (GlobalVariable gv : module.getGlobalList()) {
            currentSb.append(".align 2\n");
            currentSb.append(getLabel(gv)).append(": ");
            if (gv.isDeclaration()) {
                IrType type = ((PtrType)gv.getType()).getPointeeType();
                currentSb.append(".space ").append(getSize(type)).append("\n");
            } else {
                Constant init = (Constant)gv.getOperand(0);
                genConstant(init);
            }
        }
    }

    private void genConstant(Constant constant) {
        if (constant instanceof ConstInt ci) {
            currentSb.append(".word ").append(ci.getValue()).append("\n");
        } else if (constant instanceof ConstArray ca) {
            if (isAllZero(ca)) {
                currentSb.append(".space ").append(getSize(ca.getType())).append("\n");
            } else {
                for (Constant val : ca.getValues()) {
                    genConstant(val);
                }
            }
        } else if (constant instanceof ConstZero cz) {
            int size = getSize(cz.getType());
            currentSb.append(".space ").append(size).append("\n");
        } else if (constant instanceof ConstString cs) {
            currentSb.append(".asciiz \"").append(cs.getContent().replace("\n", "\\n").replace("\"", "\\\""))
                .append("\"\n");
        }
    }

    private boolean isAllZero(Constant constant) {
        if (constant instanceof ConstInt ci) {
            return ci.getValue() == 0;
        } else if (constant instanceof ConstArray ca) {
            for (Constant val : ca.getValues()) {
                if (!isAllZero(val))
                    return false;
            }
            return true;
        } else
            return constant instanceof ConstZero;
    }

    private int getSize(IrType type) {
        if (type instanceof IntType it) {
            return (it.getBitWidth() + 7) / 8;
        } else if (type instanceof ArrType at) {
            return at.getNumElements() * getSize(at.getElementType());
        } else if (type instanceof PtrType) {
            return 4;
        }
        return 4;
    }

    private void genText() {
        currentSb.append(".text\n");
        currentSb.append("    jal main\n");
        currentSb.append("    li $v0, 10\n");
        currentSb.append("    syscall\n\n");
        for (Function func : module.getFunctionList()) {
            if (!func.isDeclaration()) {
                genFunction(func);
            }
        }
        genRuntime();
    }

    private void genRuntime() {
        currentSb.append("\n# SysY Runtime\n");
        currentSb.append("getint:\n    li $v0, 5\n    syscall\n    jr $ra\n");
        currentSb.append("putint:\n    li $v0, 1\n    syscall\n    jr $ra\n");
        currentSb.append("putch:\n    li $v0, 11\n    syscall\n    jr $ra\n");
        currentSb.append("putstr:\n    li $v0, 4\n    syscall\n    jr $ra\n");
    }

    private List<BasicBlock> reorderBlocks(Function func, LoopAnalysis loopAnalysis) {
        List<BasicBlock> original = func.getBasicBlocks();
        if (original.isEmpty())
            return original;

        List<BasicBlock> reordered = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();

        PriorityQueue<BasicBlock> candidates = new PriorityQueue<>((b1, b2) -> {
            int d1 = loopAnalysis.getLoopDepth(b1);
            int d2 = loopAnalysis.getLoopDepth(b2);
            if (d1 != d2)
                return Integer.compare(d2, d1); // Descending depth
            return 0;
        });
        candidates.addAll(original);

        BasicBlock current = original.get(0);

        while (reordered.size() < original.size()) {
            if (current != null && !visited.contains(current)) {
                visited.add(current);
                reordered.add(current);

                // Try to extend the trace
                BasicBlock next = null;
                if (!current.getInstructions().isEmpty()) {
                    Instruction lastInst = current.getInstructions().get(current.getInstructions().size() - 1);
                    if (lastInst instanceof BrInst br) {
                        if (br.getNumOperands() == 1) {
                            BasicBlock target = (BasicBlock)br.getOperand(0);
                            if (!visited.contains(target)) {
                                next = target;
                            }
                        } else {
                            BasicBlock targetTrue = (BasicBlock)br.getOperand(1);
                            BasicBlock targetFalse = (BasicBlock)br.getOperand(2);
                            boolean trueUnvisited = !visited.contains(targetTrue);
                            boolean falseUnvisited = !visited.contains(targetFalse);

                            if (trueUnvisited && falseUnvisited) {
                                int depthTrue = loopAnalysis.getLoopDepth(targetTrue);
                                int depthFalse = loopAnalysis.getLoopDepth(targetFalse);

                                if (depthTrue > depthFalse) {
                                    next = targetTrue;
                                } else if (depthFalse > depthTrue) {
                                    next = targetFalse;
                                } else {
                                    boolean trueIsHeader = loopAnalysis.isLoopHeader(targetTrue);
                                    boolean falseIsHeader = loopAnalysis.isLoopHeader(targetFalse);
                                    if (trueIsHeader && !falseIsHeader) {
                                        next = targetTrue;
                                    } else if (!trueIsHeader && falseIsHeader) {
                                        next = targetFalse;
                                    } else {
                                        boolean trueIsExit =
                                            !targetTrue.getInstructions().isEmpty() && targetTrue.getInstructions()
                                                .get(targetTrue.getInstructions().size() - 1) instanceof ReturnInst;
                                        boolean falseIsExit =
                                            !targetFalse.getInstructions().isEmpty() && targetFalse.getInstructions()
                                                .get(targetFalse.getInstructions().size() - 1) instanceof ReturnInst;

                                        if (!trueIsExit && falseIsExit) {
                                            next = targetTrue;
                                        } else if (trueIsExit && !falseIsExit) {
                                            next = targetFalse;
                                        } else {
                                            next = targetTrue;
                                        }
                                    }
                                }
                            } else if (trueUnvisited) {
                                next = targetTrue;
                            } else if (falseUnvisited) {
                                next = targetFalse;
                            }
                        }
                    }
                }
                current = next;
            } else {
                // Trace ended, pick a new seed
                BasicBlock nextCandidate = candidates.poll();
                while (nextCandidate != null && visited.contains(nextCandidate)) {
                    nextCandidate = candidates.poll();
                }
                current = nextCandidate;
            }
        }
        return reordered;
    }

    private void genFunction(Function func) {
        currentFunction = func;
        currentSb.append(getLabel(func)).append(":\n");
        spShift = 0;

        // Register Allocation
        LivenessAnalysis liveness = new LivenessAnalysis(func);
        liveness.analyze();
        LoopAnalysis loopAnalysis = new LoopAnalysis(func);
        loopAnalysis.analyze();

        // Global Address Hoisting
        List<GlobalVariable> globals = analyzeGlobalUsage(func, loopAnalysis);
        Set<MipsRegister> excludedRegs = new HashSet<>();
        Map<GlobalVariable, MipsRegister> globalRegs = new HashMap<>();

        // Use S0-S7 for globals, limit to 3 globals
        MipsRegister[] sRegs = {MipsRegister.S0, MipsRegister.S1, MipsRegister.S2, MipsRegister.S3, MipsRegister.S4,
            MipsRegister.S5, MipsRegister.S6, MipsRegister.S7};
        int allocated = 0;
        for (GlobalVariable gv : globals) {
            if (allocated >= 8)
                break;
            MipsRegister reg = sRegs[allocated++];
            excludedRegs.add(reg);
            globalRegs.put(gv, reg);
        }

        LiveIntervalAnalysis intervalAnalysis = new LiveIntervalAnalysis(func, liveness, loopAnalysis);
        intervalAnalysis.analyze();
        this.intervals = intervalAnalysis.getIntervalMap();
        this.instToId = intervalAnalysis.getInstToId();
        GraphColoringRegAlloc allocator = new GraphColoringRegAlloc(intervalAnalysis.getIntervals(), excludedRegs,
            intervalAnalysis.getInterference());
        allocator.allocate();
        this.regMapping = allocator.getRegMapping();
        this.usedCalleeSaved = allocator.getUsedCalleeSaved();

        // Add global mappings
        for (Map.Entry<GlobalVariable, MipsRegister> entry : globalRegs.entrySet()) {
            this.regMapping.put(entry.getKey(), entry.getValue());
            this.usedCalleeSaved.add(entry.getValue());
        }

        calculateStackFrame(func);
        computePredecessors(func);

        emitPrologue(func);
        // Load global addresses into assigned registers
        for (Map.Entry<Value, MipsRegister> entry : regMapping.entrySet()) {
            if (entry.getKey() instanceof GlobalVariable gv) {
                String regName = entry.getValue().getName();
                invalidateCache(regName);
                currentSb.append("    la ").append(regName).append(", ").append(getLabel(gv)).append("\n");
            }
        }

        // Move arguments 0-3 to caller-saved registers
        List<Argument> args = func.getArguments();
        for (int i = 0; i < Math.min(args.size(), 4); i++) {
            Argument arg = args.get(i);
            MipsRegister reg = regMapping.get(arg);
            if (reg != null && reg.isCallerSaved()) {
                if (!reg.getName().equals("$a" + i)) {
                    currentSb.append("    move ").append(reg.getName()).append(", $a").append(i).append("\n");
                }
            }
        }

        List<BasicBlock> blocks = reorderBlocks(func, loopAnalysis);
        for (int i = 0; i < blocks.size(); i++) {
            BasicBlock bb = blocks.get(i);
            BasicBlock nextBb = (i + 1 < blocks.size()) ? blocks.get(i + 1) : null;

            currentSb.append(getLabel(bb)).append(":\n");
            globalAddrCache.clear();
            regToGlobal.clear();
            for (Instruction inst : bb.getInstructions()) {
                genInstruction(inst, nextBb);
            }
        }

        if (!pendingBridges.isEmpty()) {
            currentSb.append("\n# Bridges\n");
            currentSb.append(pendingBridges);
            pendingBridges.setLength(0);
        }

        currentSb.append("\n");
    }

    private List<BasicBlock> getSuccessors(BasicBlock bb) {
        List<BasicBlock> succs = new ArrayList<>();
        if (bb.getInstructions().isEmpty())
            return succs;
        Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
        if (last instanceof BrInst br) {
            if (br.getNumOperands() == 1) {
                succs.add((BasicBlock)br.getOperand(0));
            } else {
                succs.add((BasicBlock)br.getOperand(1));
                succs.add((BasicBlock)br.getOperand(2));
            }
        }
        return succs;
    }

    private void computePredecessors(Function func) {
        predecessorsMap.clear();
        for (BasicBlock bb : func.getBasicBlocks()) {
            predecessorsMap.put(bb, new ArrayList<>());
        }
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (BasicBlock succ : getSuccessors(bb)) {
                predecessorsMap.get(succ).add(bb);
            }
        }
    }

    private void updateIsLeaf() {
        isLeaf = true;
        for (BasicBlock bb : currentFunction.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst) {
                    isLeaf = false;
                    return;
                }
            }
        }
    }

    private String getJumpTarget(BasicBlock target) {
        return getLabel(target);
    }

    private void emitPrologue(Function func) {
        currentSb.append("    # Prologue\n");
        if (currentStackSize > 0) {
            addI("$sp", "$sp", -currentStackSize);
        }
        if (!isLeaf) {
            storeStack("$ra", currentStackSize - 4);
        }

        // Save callee-saved registers
        int regOffset = currentStackSize - (isLeaf ? 4 : 8);
        for (MipsRegister reg : usedCalleeSaved) {
            storeStack(reg.getName(), regOffset);
            regOffset -= 4;
        }

        // Load/Store remaining arguments
        List<Argument> args = func.getArguments();
        for (int i = 0; i < args.size(); i++) {
            Argument arg = args.get(i);
            MipsRegister reg = regMapping.get(arg);
            if (i < 4) {
                if (reg == null) { // Spilled
                    Integer offset = stackOffsets.get(arg);
                    if (offset != null) {
                        storeStack("$a" + i, offset);
                    }
                } else if (reg.isCalleeSaved()) { // Callee-saved
                    currentSb.append("    move ").append(reg.getName()).append(", $a").append(i).append("\n");
                }
            } else {
                if (reg != null) {
                    loadStack(reg.getName(), currentStackSize + (i - 4) * 4);
                } else {
                    Integer offset = stackOffsets.get(arg);
                    if (offset != null) {
                        loadStack("$t0", currentStackSize + (i - 4) * 4);
                        storeStack("$t0", offset);
                    }
                }
            }
        }
    }

    private void calculateStackFrame(Function func) {
        stackOffsets.clear();

        int offset = 0;
        // Space for arguments that are spilled
        for (Argument arg : func.getArguments()) {
            if (!regMapping.containsKey(arg)) {
                stackOffsets.put(arg, offset);
                offset += 4;
            }
        }

        // Space for alloca and other spills
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca) {
                    int size = getSize(((PtrType)alloca.getType()).getPointeeType());
                    size = (size + 3) / 4 * 4; // Align to 4 bytes
                    stackOffsets.put(inst, offset);
                    offset += size;
                } else if (!(inst.getType() instanceof NoneType) && !regMapping.containsKey(inst)) {
                    stackOffsets.put(inst, offset);
                    offset += 4;
                }
            }
        }

        updateIsLeaf();

        int localsSize = offset;
        int calleeSavedSize = usedCalleeSaved.size() * 4;
        int raSize = isLeaf ? 0 : 4;

        currentStackSize = localsSize + calleeSavedSize + raSize;
        currentStackSize = (currentStackSize + 7) / 8 * 8;
    }

    private boolean hasPhis(BasicBlock current, BasicBlock target) {
        for (Instruction inst : target.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                Value incoming = phi.getIncomingValue(current);
                if (incoming != null && incoming != phi) {
                    return true;
                }
            } else {
                break;
            }
        }
        return false;
    }

    private void genInstruction(Instruction inst, BasicBlock nextBb) {
        // currentSb.append(" # ").append(inst.toString()).append("\n");
        switch (inst.getOpCode()) {
            case ADD, SUB, MUL, SDIV, SREM -> genBinary(inst);
            case ICMP -> genIcmp((IcmpInst)inst);
            case ALLOCA -> genAlloca();
            case LOAD -> genLoad((LoadInst)inst);
            case STORE -> genStore((StoreInst)inst);
            case BR -> genBr((BrInst)inst, nextBb);
            case RET -> genRet((ReturnInst)inst);
            case CALL -> genCall((CallInst)inst);
            case ZEXT -> genZext((ZextInst)inst);
            case GEP -> genGep((GetElementPtrInst)inst);
            case PHI -> {
            } // Handled by predecessors
        }
    }

    private void loadValue(Value val, String reg) {
        if (val instanceof ConstInt ci) {
            invalidateCache(reg);
            currentSb.append("    li ").append(reg).append(", ").append(ci.getValue()).append("\n");
            return;
        }

        if (val instanceof GetElementPtrInst gep) {
            ConstGlobalAddr addr = tryBuildConstGlobalAddr(gep);
            if (addr != null) {
                materializeConstGlobalAddr(addr, reg);
                return;
            }
        }

        if (val instanceof GlobalValue gv) {
            if (regMapping.containsKey(gv)) {
                MipsRegister srcReg = regMapping.get(gv);
                invalidateCache(reg);
                if (!srcReg.getName().equals(reg)) {
                    currentSb.append("    move ").append(reg).append(", ").append(srcReg.getName()).append("\n");
                }
                return;
            }
            if (globalAddrCache.containsKey(gv)) {
                String cachedReg = globalAddrCache.get(gv);
                if (!cachedReg.equals(reg)) {
                    invalidateCache(reg);
                    currentSb.append("    move ").append(reg).append(", ").append(cachedReg).append("\n");
                }
            } else {
                invalidateCache(reg);
                currentSb.append("    la ").append(reg).append(", ").append(getLabel(gv)).append("\n");
                updateCache(gv, reg);
            }
            return;
        }

        if (val instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            addI(reg, "$sp", dataOffset + spShift);
            return;
        }

        if (regMapping.containsKey(val)) {
            MipsRegister srcReg = regMapping.get(val);
            if (!srcReg.getName().equals(reg)) {
                invalidateCache(reg);
                currentSb.append("    move ").append(reg).append(", ").append(srcReg.getName()).append("\n");
            }
            return;
        }

        Integer offset = stackOffsets.get(val);
        if (offset == null) {
            throw new RuntimeException("Value not found in stack or register: " + val);
        }
        loadStack(reg, offset + spShift);
    }

    private String getValueReg(Value val, String tempReg) {
        if (val instanceof ConstInt ci) {
            if (ci.getValue() == 0)
                return "$zero";
            invalidateCache(tempReg);
            currentSb.append("    li ").append(tempReg).append(", ").append(ci.getValue()).append("\n");
            return tempReg;
        }

        if (val instanceof GetElementPtrInst gep) {
            ConstGlobalAddr addr = tryBuildConstGlobalAddr(gep);
            if (addr != null) {
                materializeConstGlobalAddr(addr, tempReg);
                return tempReg;
            }
        }

        if (val instanceof GlobalValue gv) {
            if (regMapping.containsKey(gv)) {
                return regMapping.get(gv).getName();
            }
            if (globalAddrCache.containsKey(gv)) {
                return globalAddrCache.get(gv);
            }
            invalidateCache(tempReg);
            currentSb.append("    la ").append(tempReg).append(", ").append(getLabel(gv)).append("\n");
            updateCache(gv, tempReg);
            return tempReg;
        }

        if (val instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            addI(tempReg, "$sp", dataOffset + spShift);
            return tempReg;
        }

        if (regMapping.containsKey(val)) {
            return regMapping.get(val).getName();
        }

        Integer offset = stackOffsets.get(val);
        if (offset == null) {
            throw new RuntimeException("Value not found in stack or register: " + val);
        }
        loadStack(tempReg, offset + spShift);
        return tempReg;
    }

    private String getDestReg(Instruction inst, String tempReg) {
        if (regMapping.containsKey(inst)) {
            return regMapping.get(inst).getName();
        }
        return tempReg;
    }

    private void storeValue(Value inst) {
        if (regMapping.containsKey(inst)) {
            MipsRegister destReg = regMapping.get(inst);
            if (!destReg.getName().equals("$v0")) {
                invalidateCache(destReg.getName());
                currentSb.append("    move ").append(destReg.getName()).append(", ").append(
                "$v0").append("\n");
            }
        } else {
            Integer offset = stackOffsets.get(inst);
            if (offset != null) {
                storeStack("$v0", offset + spShift);
            }
        }
    }

    private void genBinary(Instruction inst) {
        Value op1 = inst.getOperand(0);
        Value op2 = inst.getOperand(1);
        String rd = getDestReg(inst, "$t2");

        if (inst.getOpCode() == OpCode.SDIV && op2 instanceof ConstInt ci) {
            if (emitConstDivision(op1, ci.getValue(), rd)) {
                if (!regMapping.containsKey(inst))
                    storeStack(rd, stackOffsets.get(inst) + spShift);
                return;
            }
        }

        if (inst.getOpCode() == OpCode.SREM && op2 instanceof ConstInt ci) {
            if (emitConstDivision(op1, ci.getValue(), "$t2")) {
                loadValue(op1, "$t0");
                invalidateCache("$t3");
                currentSb.append("    li $t3, ").append(ci.getValue()).append("\n");
                invalidateCache("$t1");
                currentSb.append("    mul $t1, $t2, $t3\n");
                invalidateCache(rd);
                currentSb.append("    subu ").append(rd).append(", $t0, $t1\n");
                if (!regMapping.containsKey(inst))
                    storeStack(rd, stackOffsets.get(inst) + spShift);
                return;
            }
        }

        if (inst.getOpCode() == OpCode.MUL) {
            if (op2 instanceof ConstInt ci && tryConstMul(inst, op1, ci.getValue(), rd)) {
                return;
            }
            if (op1 instanceof ConstInt ci && tryConstMul(inst, op2, ci.getValue(), rd)) {
                return;
            }
        }

        if (inst.getOpCode() == OpCode.ADD && op2 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            String r1 = getValueReg(op1, "$t0");
            invalidateCache(rd);
            addI(rd, r1, ci.getValue());
            if (!regMapping.containsKey(inst)) {
                storeStack(rd, stackOffsets.get(inst) + spShift);
            }
        } else if (inst.getOpCode() == OpCode.ADD && op1 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            String r2 = getValueReg(op2, "$t0");
            invalidateCache(rd);
            addI(rd, r2, ci.getValue());
            if (!regMapping.containsKey(inst)) {
                storeStack(rd, stackOffsets.get(inst) + spShift);
            }
        } else if (inst.getOpCode() == OpCode.SUB && op2 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            String r1 = getValueReg(op1, "$t0");
            invalidateCache(rd);
            addI(rd, r1, -ci.getValue());
            if (!regMapping.containsKey(inst)) {
                storeStack(rd, stackOffsets.get(inst) + spShift);
            }
        } else {
            String r1 = getValueReg(op1, "$t0");
            String tempForR2 = r1.equals("$t1") ? "$t0" : "$t1";
            String r2 = getValueReg(op2, tempForR2);
            invalidateCache(rd);
            switch (inst.getOpCode()) {
                case ADD -> currentSb.append("    addu ").append(rd).append(", ").append(r1).append(", ").append(r2)
                    .append("\n");
                case SUB -> currentSb.append("    subu ").append(rd).append(", ").append(r1).append(", ").append(r2)
                    .append("\n");
                case MUL -> currentSb.append("    mul ").append(rd).append(", ").append(r1).append(", ").append(r2)
                    .append("\n");
                case SDIV -> currentSb.append("    div ").append(r1).append(", ").append(r2).append("\n    mflo ")
                    .append(rd).append("\n");
                case SREM -> currentSb.append("    div ").append(r1).append(", ").append(r2).append("\n    mfhi ")
                    .append(rd).append("\n");
            }
            if (!regMapping.containsKey(inst)) {
                storeStack(rd, stackOffsets.get(inst) + spShift);
            }
        }
    }

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private int log2(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    private boolean tryConstMul(Instruction inst, Value multiplicand, int constant, String destReg) {
        if (constant == 0) {
            invalidateCache(destReg);
            currentSb.append("    addu ").append(destReg).append(", $zero, $zero\n");
            if (!regMapping.containsKey(inst))
                storeStack(destReg, stackOffsets.get(inst) + spShift);
            return true;
        }

        long absConst = Math.abs((long)constant);
        List<MulTerm> terms = buildConstMulTerms(absConst);
        if (terms.isEmpty()) {
            return false;
        }

        int firstIndex = -1;
        for (int i = 0; i < terms.size(); i++) {
            MulTerm term = terms.get(i);
            if (term.positive && (firstIndex == -1 || terms.get(firstIndex).shift < term.shift)) {
                firstIndex = i;
            }
        }
        if (firstIndex == -1) {
            for (int i = 0; i < terms.size(); i++) {
                if (firstIndex == -1 || terms.get(firstIndex).shift < terms.get(i).shift) {
                    firstIndex = i;
                }
            }
        }
        MulTerm firstTerm = terms.get(firstIndex);
        List<MulTerm> remaining = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            if (i != firstIndex) {
                remaining.add(terms.get(i));
            }
        }
        remaining.sort((a, b) -> Integer.compare(b.shift, a.shift));

        int instructionCount = 1; // build first term
        if (!firstTerm.positive) {
            instructionCount++;
        }
        for (MulTerm term : remaining) {
            instructionCount++; // add/sub
            if (term.shift != 0) {
                instructionCount++; // shift temp
            }
        }
        if (constant < 0) {
            instructionCount++;
        }
        if (instructionCount >= 5) {
            return false;
        }

        loadValue(multiplicand, "$t0");
        invalidateCache(destReg);
        emitMulTerm(firstTerm, destReg);
        if (!firstTerm.positive) {
            invalidateCache(destReg);
            currentSb.append("    subu ").append(destReg).append(", $zero, ").append(destReg).append("\n");
        }
        for (MulTerm term : remaining) {
            if (term.shift == 0) {
                invalidateCache(destReg);
                if (term.positive) {
                    currentSb.append("    addu ").append(destReg).append(", ").append(destReg).append(", $t0\n");
                } else {
                    currentSb.append("    subu ").append(destReg).append(", ").append(destReg).append(", $t0\n");
                }
            } else {
                invalidateCache("$t1", destReg);
                currentSb.append("    sll $t1, $t0, ").append(term.shift).append("\n");
                if (term.positive) {
                    currentSb.append("    addu ").append(destReg).append(", ").append(destReg).append(", $t1\n");
                } else {
                    currentSb.append("    subu ").append(destReg).append(", ").append(destReg).append(", $t1\n");
                }
            }
        }
        if (constant < 0) {
            invalidateCache(destReg);
            currentSb.append("    subu ").append(destReg).append(", $zero, ").append(destReg).append("\n");
        }
        if (!regMapping.containsKey(inst))
            storeStack(destReg, stackOffsets.get(inst) + spShift);
        return true;
    }

    private boolean emitConstDivision(Value dividend, int divisor, String destReg) {
        if (divisor == 0) {
            return false;
        }
        if (divisor == 1) {
            loadValue(dividend, "$t0");
            invalidateCache(destReg);
            currentSb.append("    addu ").append(destReg).append(", $t0, $zero\n");
            return true;
        }
        if (divisor == -1) {
            loadValue(dividend, "$t0");
            invalidateCache(destReg);
            currentSb.append("    subu ").append(destReg).append(", $zero, $t0\n");
            return true;
        }
        long absDiv = Math.abs((long)divisor);
        if ((absDiv & absDiv - 1) == 0) {
            int shift = Long.numberOfTrailingZeros(absDiv);
            if (shift > 0) {
                loadValue(dividend, "$t0");
                invalidateCache("$t1");
                currentSb.append("    sra $t1, $t0, 31\n");
                currentSb.append("    srl $t1, $t1, ").append(32 - shift).append("\n");
                invalidateCache("$t0");
                currentSb.append("    addu $t0, $t0, $t1\n");
                invalidateCache(destReg);
                currentSb.append("    sra ").append(destReg).append(", $t0, ").append(shift).append("\n");
                if (divisor < 0) {
                    invalidateCache(destReg);
                    currentSb.append("    subu ").append(destReg).append(", $zero, ").append(destReg).append("\n");
                }
                return true;
            }
        }
        DivOptimizer.MultiplierInfo info = DivOptimizer.chooseMultiplier(divisor);
        loadValue(dividend, "$t0");
        int magic = (int)info.multiplier();
        invalidateCache("$t1");
        currentSb.append("    li $t1, ").append(magic).append("\n");
        invalidateCache(destReg);
        currentSb.append("    mult $t0, $t1\n");
        currentSb.append("    mfhi ").append(destReg).append("\n");
        if (magic < 0) {
            invalidateCache(destReg);
            currentSb.append("    addu ").append(destReg).append(", ").append(destReg).append(", $t0\n");
        }
        if (info.shift() > 0) {
            invalidateCache(destReg);
            currentSb.append("    sra ").append(destReg).append(", ").append(destReg).append(", ").append(info.shift())
                .append("\n");
        }
        invalidateCache("$t3");
        currentSb.append("    srl $t3, $t0, 31\n");
        invalidateCache(destReg);
        currentSb.append("    addu ").append(destReg).append(", ").append(destReg).append(", $t3\n");
        if (divisor < 0) {
            invalidateCache(destReg);
            currentSb.append("    subu ").append(destReg).append(", $zero, ").append(destReg).append("\n");
        }
        return true;
    }

    private void emitMulTerm(MulTerm term, String destReg) {
        if (term.shift == 0) {
            currentSb.append("    addu ").append(destReg).append(", $t0, $zero\n");
        } else {
            currentSb.append("    sll ").append(destReg).append(", $t0, ").append(term.shift).append("\n");
        }
    }

    private List<MulTerm> buildConstMulTerms(long value) {
        List<MulTerm> terms = new ArrayList<>();
        if (value == 0) {
            return terms;
        }
        long n = value;
        int shift = 0;
        while (n > 0) {
            if ((n & 1L) == 0) {
                n >>= 1;
            } else {
                long remainder = n & 3L;
                boolean positive = remainder == 1L;
                long digit = positive ? 1L : -1L;
                terms.add(new MulTerm(shift, positive));
                n = (n - digit) >> 1;
            }
            shift++;
        }
        return terms;
    }

    private boolean isFusableIcmp(IcmpInst inst) {
        List<Use> uses = inst.getUseList();
        if (uses.size() != 1) {
            return false;
        }
        User user = uses.get(0).user();
        if (!(user instanceof BrInst br)) {
            return false;
        }
        return br.getParent() == inst.getParent() && br.getOperand(0) == inst;
    }

    private String getBranchInst(String predicate, boolean jumpIfTrue) {
        if (jumpIfTrue) {
            return switch (predicate) {
                case "eq" -> "beq";
                case "ne" -> "bne";
                case "sgt" -> "bgt";
                case "sge" -> "bge";
                case "slt" -> "blt";
                case "sle" -> "ble";
                default -> throw new RuntimeException("Unknown predicate: " + predicate);
            };
        } else {
            return switch (predicate) {
                case "eq" -> "bne";
                case "ne" -> "beq";
                case "sgt" -> "ble";
                case "sge" -> "blt";
                case "slt" -> "bge";
                case "sle" -> "bgt";
                default -> throw new RuntimeException("Unknown predicate: " + predicate);
            };
        }
    }

    private void genIcmp(IcmpInst inst) {
        if (isFusableIcmp(inst)) {
            return;
        }
        String r0 = getValueReg(inst.getOperand(0), "$t0");
        String tempForR1 = r0.equals("$t1") ? "$t0" : "$t1";
        String r1 = getValueReg(inst.getOperand(1), tempForR1);
        String cond = inst.getPredicate().toString();
        String rd = getDestReg(inst, "$t2");
        switch (cond) {
            case "eq" ->
                currentSb.append("    seq ").append(rd).append(", ").append(r0).append(", ").append(r1).append("\n");
            case "ne" ->
                currentSb.append("    sne ").append(rd).append(", ").append(r0).append(", ").append(r1).append("\n");
            case "sgt" ->
                currentSb.append("    sgt ").append(rd).append(", ").append(r0).append(", ").append(r1).append("\n");
            case "sge" ->
                currentSb.append("    sge ").append(rd).append(", ").append(r0).append(", ").append(r1).append("\n");
            case "slt" ->
                currentSb.append("    slt ").append(rd).append(", ").append(r0).append(", ").append(r1).append("\n");
            case "sle" ->
                currentSb.append("    sle ").append(rd).append(", ").append(r0).append(", ").append(r1).append("\n");
        }
        if (!regMapping.containsKey(inst))
            storeStack(rd, stackOffsets.get(inst) + spShift);
    }

    @SuppressWarnings("EmptyMethod")
    private void genAlloca() {
        // Space reserved in calculateStackFrame
    }

    private int getAllocaDataOffset(AllocaInst alloca) {
        Integer offset = stackOffsets.get(alloca);
        if (offset == null) {
            throw new RuntimeException("Missing stack slot for alloca: " + alloca);
        }
        return offset;
    }

    private boolean allIndicesConstant(GetElementPtrInst gep) {
        for (int i = 1; i < gep.getNumOperands(); i++) {
            if (!(gep.getOperand(i) instanceof ConstInt)) {
                return false;
            }
        }
        return true;
    }

    private int calculateTotalOffset(GetElementPtrInst gep) {
        IrType currentType = ((PtrType)gep.getOperand(0).getType()).getPointeeType();
        int totalOffset = 0;

        for (int i = 1; i < gep.getNumOperands(); i++) {
            Value index = gep.getOperand(i);
            int elementSize;
            if (i == 1) {
                elementSize = getSize(currentType);
            } else {
                if (currentType instanceof ArrType at) {
                    currentType = at.getElementType();
                    elementSize = getSize(currentType);
                } else {
                    elementSize = 4;
                }
            }

            if (index instanceof ConstInt ci) {
                totalOffset += ci.getValue() * elementSize;
            }
        }
        return totalOffset;
    }

    private boolean canFuseGep(GetElementPtrInst gep) {
        if (regMapping.containsKey(gep.getOperand(0))) {
            return false;
        }
        return allIndicesConstant(gep);
    }

    private void genLoad(LoadInst inst) {
        Value addr = inst.getOperand(0);
        String destReg = getDestReg(inst, "$t1");
        invalidateCache(destReg);

        if (addr instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            loadStack(destReg, dataOffset + spShift);
        } else if (addr instanceof GetElementPtrInst gep && canFuseGep(gep)) {
            int offset = calculateTotalOffset(gep);
            if (offset >= -32768 && offset <= 32767) {
                String baseReg = getValueReg(gep.getOperand(0), "$t0");
                currentSb.append("    lw ").append(destReg).append(", ").append(offset).append("(").append(baseReg)
                    .append(")\n");
            } else {
                String addrReg = getValueReg(addr, "$t0");
                currentSb.append("    lw ").append(destReg).append(", 0(").append(addrReg).append(")\n");
            }
        } else {
            String addrReg = getValueReg(addr, "$t0");
            currentSb.append("    lw ").append(destReg).append(", 0(").append(addrReg).append(")\n");
        }

        if (!regMapping.containsKey(inst)) {
            storeStack(destReg, stackOffsets.get(inst) + spShift);
        }
    }

    private void genStore(StoreInst inst) {
        Value val = inst.getOperand(0);
        Value addr = inst.getOperand(1);

        String valReg = getValueReg(val, "$t0");

        if (addr instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            storeStack(valReg, dataOffset + spShift);
        } else if (addr instanceof GetElementPtrInst gep && canFuseGep(gep)) {
            int offset = calculateTotalOffset(gep);
            if (offset >= -32768 && offset <= 32767) {
                String baseReg = getValueReg(gep.getOperand(0), "$t1");
                currentSb.append("    sw ").append(valReg).append(", ").append(offset).append("(").append(baseReg)
                    .append(")\n");
            } else {
                String addrReg = getValueReg(addr, "$t1");
                currentSb.append("    sw ").append(valReg).append(", 0(").append(addrReg).append(")\n");
            }
        } else {
            String addrReg = getValueReg(addr, "$t1");
            currentSb.append("    sw ").append(valReg).append(", 0(").append(addrReg).append(")\n");
        }
    }

    private void emitConditionalBranch(IcmpInst icmp, String r0, String r1, boolean jumpIfTrue, BasicBlock target,
        boolean hasPhis, BasicBlock current) {
        String label = getJumpTarget(target);
        String branchLabel = label;
        if (hasPhis) {
            branchLabel = "br_bridge_" + (brCounter++);
        }

        if (icmp != null) {
            String bInst = getBranchInst(icmp.getPredicate().toString(), jumpIfTrue);
            currentSb.append("    ").append(bInst).append(" ").append(r0).append(", ").append(r1).append(", ")
                .append(branchLabel).append("\n");
        } else {
            String bInst = jumpIfTrue ? "bne" : "beq";
            currentSb.append("    ").append(bInst).append(" ").append(r0).append(", $zero, ").append(branchLabel)
                .append("\n");
        }

        if (hasPhis) {
            StringBuilder oldSb = currentSb;
            currentSb = pendingBridges;
            currentSb.append(branchLabel).append(":\n");
            fillPhis(current, target);
            currentSb.append("    j ").append(label).append("\n");
            currentSb = oldSb;
        }
    }

    private void genBr(BrInst inst, BasicBlock nextBb) {
        if (inst.getNumOperands() == 1) {
            BasicBlock target = (BasicBlock)inst.getOperand(0);
            fillPhis(inst.getParent(), target);
            if (target != nextBb) {
                currentSb.append("    j ").append(getJumpTarget(target)).append("\n");
            }
        } else {
            Value cond = inst.getOperand(0);
            BasicBlock targetTrue = (BasicBlock)inst.getOperand(1);
            BasicBlock targetFalse = (BasicBlock)inst.getOperand(2);

            IcmpInst icmp = null;
            if (cond instanceof IcmpInst && isFusableIcmp((IcmpInst)cond)) {
                icmp = (IcmpInst)cond;
            }

            String r0;
            String r1 = "$t1";
            if (icmp != null) {
                r0 = getValueReg(icmp.getOperand(0), "$t0");
                String tempForR1 = r0.equals("$t1") ? "$t0" : "$t1";
                r1 = getValueReg(icmp.getOperand(1), tempForR1);
            } else {
                r0 = getValueReg(cond, "$t0");
            }

            boolean phisTrue = hasPhis(inst.getParent(), targetTrue);
            boolean phisFalse = hasPhis(inst.getParent(), targetFalse);

            if (targetFalse == nextBb) {
                // Fall through to False
                emitConditionalBranch(icmp, r0, r1, true, targetTrue, phisTrue, inst.getParent());
                fillPhis(inst.getParent(), targetFalse);
            } else if (targetTrue == nextBb) {
                // Fall through to True
                emitConditionalBranch(icmp, r0, r1, false, targetFalse, phisFalse, inst.getParent());
                fillPhis(inst.getParent(), targetTrue);
            } else {
                // Neither is next
                emitConditionalBranch(icmp, r0, r1, false, targetFalse, phisFalse, inst.getParent());
                fillPhis(inst.getParent(), targetTrue);
                currentSb.append("    j ").append(getJumpTarget(targetTrue)).append("\n");
            }
        }
    }

    private void fillPhis(BasicBlock current, BasicBlock target) {
        List<PhiInst> phis = new ArrayList<>();
        for (Instruction inst : target.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                phis.add(phi);
            } else {
                break;
            }
        }

        if (phis.isEmpty()) {
            return;
        }
        Map<String, PhiCopy> copies = new LinkedHashMap<>();
        for (PhiInst phi : phis) {
            Value incoming = phi.getIncomingValue(current);
            if (incoming == null || incoming == phi) {
                continue;
            }
            PhiLoc dst = getPhiLoc(phi);
            if (dst == null) {
                continue;
            }
            PhiSrc src = getPhiSrc(incoming);
            if (src.loc != null && src.loc.key.equals(dst.key)) {
                continue;
            }
            copies.put(dst.key, new PhiCopy(dst, src));
        }

        if (copies.isEmpty()) {
            return;
        }

        while (!copies.isEmpty()) {
            Set<String> srcLocs = new LinkedHashSet<>();
            for (PhiCopy copy : copies.values()) {
                if (copy.src.loc != null) {
                    srcLocs.add(copy.src.loc.key);
                }
            }

            boolean progressed = false;
            for (java.util.Iterator<Map.Entry<String, PhiCopy>> it = copies.entrySet().iterator(); it.hasNext();) {
                PhiCopy copy = it.next().getValue();
                if (!srcLocs.contains(copy.dst.key)) {
                    emitPhiCopy(copy);
                    it.remove();
                    progressed = true;
                    break;
                }
            }

            if (progressed) {
                continue;
            }

            PhiCopy first = copies.values().iterator().next();
            if (first.src.loc == null) {
                emitPhiCopy(first);
                copies.remove(first.dst.key);
                continue;
            }

            PhiLoc start = first.dst;
            loadFromPhiLoc(start, "$t0");

            PhiLoc currentDst = start;
            PhiLoc currentSrc = first.src.loc;
            while (!currentSrc.key.equals(start.key)) {
                loadFromPhiLoc(currentSrc, "$t1");
                storeToPhiLoc(currentDst, "$t1");
                copies.remove(currentDst.key);

                PhiCopy next = copies.get(currentSrc.key);
                if (next == null || next.src.loc == null) {
                    currentDst = currentSrc;
                    break;
                }
                currentDst = currentSrc;
                currentSrc = next.src.loc;
            }

            storeToPhiLoc(currentDst, "$t0");
            copies.remove(currentDst.key);
        }
    }

    private PhiLoc getPhiLoc(Value v) {
        MipsRegister reg = regMapping.get(v);
        if (reg != null) {
            return new PhiLoc("R:" + reg.getName(), reg.getName(), null);
        }
        Integer offset = stackOffsets.get(v);
        if (offset != null) {
            return new PhiLoc("S:" + offset, null, offset);
        }
        return null;
    }

    private PhiSrc getPhiSrc(Value v) {
        if (v instanceof ConstInt || v instanceof GlobalValue || v instanceof AllocaInst) {
            return new PhiSrc(null, v);
        }
        if (v instanceof GetElementPtrInst gep && !regMapping.containsKey(v) && tryBuildConstGlobalAddr(gep) != null) {
            return new PhiSrc(null, v);
        }
        PhiLoc loc = getPhiLoc(v);
        return new PhiSrc(loc, loc != null ? null : v);
    }

    private void loadFromPhiLoc(PhiLoc loc, String destReg) {
        if (loc.reg != null) {
            if (!loc.reg.equals(destReg)) {
                invalidateCache(destReg);
                currentSb.append("    move ").append(destReg).append(", ").append(loc.reg).append("\n");
            }
            return;
        }
        loadStack(destReg, loc.stackOffset + spShift);
    }

    private void storeToPhiLoc(PhiLoc loc, String srcReg) {
        if (loc.reg != null) {
            if (!loc.reg.equals(srcReg)) {
                invalidateCache(loc.reg);
                currentSb.append("    move ").append(loc.reg).append(", ").append(srcReg).append("\n");
            }
            return;
        }
        storeStack(srcReg, loc.stackOffset + spShift);
    }

    private void emitPhiCopy(PhiCopy copy) {
        PhiLoc dst = copy.dst;
        PhiSrc src = copy.src;
        if (dst.reg != null) {
            if (src.loc != null) {
                if (src.loc.reg != null) {
                    if (!dst.reg.equals(src.loc.reg)) {
                        invalidateCache(dst.reg);
                        currentSb.append("    move ").append(dst.reg).append(", ").append(src.loc.reg).append("\n");
                    }
                } else {
                    loadStack(dst.reg, src.loc.stackOffset + spShift);
                }
            } else {
                loadValue(src.value, dst.reg);
            }
            return;
        }

        if (src.loc != null) {
            if (src.loc.reg != null) {
                storeStack(src.loc.reg, dst.stackOffset + spShift);
            } else {
                loadStack("$t1", src.loc.stackOffset + spShift);
                storeStack("$t1", dst.stackOffset + spShift);
            }
        } else {
            loadValue(src.value, "$t1");
            storeStack("$t1", dst.stackOffset + spShift);
        }
    }

    private record PhiLoc(String key, String reg, Integer stackOffset) {
    }

    private record PhiSrc(PhiLoc loc, Value value) {
    }

    private record PhiCopy(PhiLoc dst, PhiSrc src) {
    }

    private void genRet(ReturnInst inst) {
        if (inst.getNumOperands() > 0) {
            loadValue(inst.getOperand(0), "$v0");
        }

        if (currentStackSize > 0) {
            // Restore callee-saved registers
            int regOffset = currentStackSize - (isLeaf ? 4 : 8);
            for (MipsRegister reg : usedCalleeSaved) {
                loadStack(reg.getName(), regOffset);
                regOffset -= 4;
            }

            if (!isLeaf) {
                loadStack("$ra", currentStackSize - 4);
            }
            addI("$sp", "$sp", currentStackSize);
        }
        currentSb.append("    jr $ra\n");
    }

    private void genCall(CallInst inst) {
        Function target = (Function)inst.getOperand(0);
        int numArgs = inst.getNumOperands() - 1;
        boolean clobbersCallerSaved = !isRuntimeSyscallWrapper(target);

        // Save caller-saved registers that are live across this call
        int instId = instToId.get(inst);
        List<MipsRegister> toSave = new ArrayList<>();
        if (clobbersCallerSaved) {
            for (Map.Entry<Value, MipsRegister> entry : regMapping.entrySet()) {
                MipsRegister reg = entry.getValue();
                if (reg.isCallerSaved()) {
                    LiveInterval interval = intervals.get(entry.getKey());
                    if (interval != null && interval.getStart() < instId && interval.getEnd() > instId) {
                        toSave.add(reg);
                    }
                }
            }
        }

        // Save to stack (below current sp)
        if (!toSave.isEmpty()) {
            int shift = toSave.size() * 4;
            if (shift % 8 != 0) {
                shift += 4;
            }
            addI("$sp", "$sp", -shift);
            spShift += shift;
            for (int i = 0; i < toSave.size(); i++) {
                storeStack(toSave.get(i).getName(), i * 4);
            }
        }

        int stackArgs = Math.max(0, numArgs - 4);
        int stackSpace = (stackArgs * 4 + 7) / 8 * 8;

        if (stackSpace > 0) {
            addI("$sp", "$sp", -stackSpace);
            spShift += stackSpace;
        }

        for (int i = 1; i < inst.getNumOperands(); i++) {
            Value arg = inst.getOperand(i);
            if (i - 1 < 4) {
                loadValue(arg, "$a" + (i - 1));
            } else {
                String reg = getValueReg(arg, "$t0");
                int offset = (i - 1 - 4) * 4;
                storeStack(reg, offset);
            }
        }
        currentSb.append("    jal ").append(getLabel(target)).append("\n");
        if (clobbersCallerSaved) {
            invalidateCallerSaved();
        }

        if (stackSpace > 0) {
            addI("$sp", "$sp", stackSpace);
            spShift -= stackSpace;
        }

        // Restore caller-saved registers
        if (!toSave.isEmpty()) {
            int shift = toSave.size() * 4;
            if (shift % 8 != 0) {
                shift += 4;
            }
            for (int i = 0; i < toSave.size(); i++) {
                loadStack(toSave.get(i).getName(), i * 4);
            }
            addI("$sp", "$sp", shift);
            spShift -= shift;
        }

        if (!(inst.getType() instanceof NoneType)) {
            storeValue(inst);
        }
    }

    private boolean isRuntimeSyscallWrapper(Function target) {
        String name = target.getName();
        return name.equals("getint") || name.equals("putint") || name.equals("putch") || name.equals("putstr");
    }

    private void genZext(ZextInst inst) {
        String dst = getDestReg(inst, "$t0");
        loadValue(inst.getOperand(0), dst);
        if (!regMapping.containsKey(inst))
            storeStack(dst, stackOffsets.get(inst) + spShift);
    }

    private void genGep(GetElementPtrInst inst) {
        if (!regMapping.containsKey(inst) && tryBuildConstGlobalAddr(inst) != null) {
            // Rematerializable constant-address GEP: skip emission here, materialize at each use.
            return;
        }
        String dst = getDestReg(inst, "$t0");
        String accum = dst;
        boolean conflict = dst.equals("$t1") || dst.equals("$t2");
        if (!conflict) {
            for (int i = 1; i < inst.getNumOperands(); i++) {
                Value index = inst.getOperand(i);
                if (regMapping.containsKey(index) && regMapping.get(index).getName().equals(dst)) {
                    conflict = true;
                    break;
                }
            }
        }

        if (conflict) {
            String[] candidates = {"$t0", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9"};
            for (String cand : candidates) {
                boolean used = false;
                for (int i = 1; i < inst.getNumOperands(); i++) {
                    Value index = inst.getOperand(i);
                    if (regMapping.containsKey(index) && regMapping.get(index).getName().equals(cand)) {
                        used = true;
                        break;
                    }
                }
                if (!used) {
                    accum = cand;
                    break;
                }
            }
        }

        loadValue(inst.getOperand(0), accum);

        IrType currentType = ((PtrType)inst.getOperand(0).getType()).getPointeeType();

        for (int i = 1; i < inst.getNumOperands(); i++) {
            Value index = inst.getOperand(i);
            int elementSize;
            if (i == 1) {
                elementSize = getSize(currentType);
            } else {
                if (currentType instanceof ArrType at) {
                    currentType = at.getElementType();
                    elementSize = getSize(currentType);
                } else {
                    elementSize = 4;
                }
            }

            if (index instanceof ConstInt ci) {
                int offset = ci.getValue() * elementSize;
                if (offset != 0) {
                    addI(accum, accum, offset);
                }
            } else {
                String idxReg = getValueReg(index, "$t1");
                if (elementSize == 1) {
                    invalidateCache(accum);
                    currentSb.append("    addu ").append(accum).append(", ").append(accum).append(", ").append(idxReg)
                        .append("\n");
                } else if (isPowerOfTwo(elementSize)) {
                    invalidateCache("$t1");
                    currentSb.append("    sll $t1, ").append(idxReg).append(", ").append(log2(elementSize))
                        .append("\n");
                    invalidateCache(accum);
                    currentSb.append("    addu ").append(accum).append(", ").append(accum).append(", $t1\n");
                } else {
                    String sizeReg = idxReg.equals("$t2") ? "$t1" : "$t2";
                    invalidateCache(sizeReg);
                    currentSb.append("    li ").append(sizeReg).append(", ").append(elementSize).append("\n");
                    invalidateCache("$t1");
                    currentSb.append("    mul $t1, ").append(idxReg).append(", ").append(sizeReg).append("\n");
                    invalidateCache(accum);
                    currentSb.append("    addu ").append(accum).append(", ").append(accum).append(", $t1\n");
                }
            }
        }

        if (conflict) {
            invalidateCache(dst);
            currentSb.append("    move ").append(dst).append(", ").append(accum).append("\n");
        }

        if (!regMapping.containsKey(inst))
            storeStack(dst, stackOffsets.get(inst) + spShift);
    }

    private record ConstGlobalAddr(GlobalVariable base, int offset) {
    }

    private ConstGlobalAddr tryBuildConstGlobalAddr(Value value) {
        if (value instanceof GlobalVariable gv) {
            return new ConstGlobalAddr(gv, 0);
        }
        if (!(value instanceof GetElementPtrInst gep)) {
            return null;
        }

        ConstGlobalAddr baseAddr = tryBuildConstGlobalAddr(gep.getOperand(0));
        if (baseAddr == null) {
            return null;
        }

        long offset = baseAddr.offset;
        IrType currentType = ((PtrType)gep.getOperand(0).getType()).getPointeeType();
        for (int i = 1; i < gep.getNumOperands(); i++) {
            Value index = gep.getOperand(i);
            if (!(index instanceof ConstInt ci)) {
                return null;
            }

            int elementSize;
            if (i == 1) {
                elementSize = getSize(currentType);
            } else {
                if (currentType instanceof ArrType at) {
                    currentType = at.getElementType();
                    elementSize = getSize(currentType);
                } else {
                    elementSize = 4;
                }
            }

            offset += (long)ci.getValue() * elementSize;
            if (offset < Integer.MIN_VALUE || offset > Integer.MAX_VALUE) {
                return null;
            }
        }

        return new ConstGlobalAddr(baseAddr.base, (int)offset);
    }

    private void materializeConstGlobalAddr(ConstGlobalAddr addr, String reg) {
        loadValue(addr.base, reg);
        if (addr.offset != 0) {
            addI(reg, reg, addr.offset);
        }
    }

    private List<GlobalVariable> analyzeGlobalUsage(Function func, LoopAnalysis loopAnalysis) {
        Map<GlobalVariable, Double> scores = new HashMap<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            int depth = loopAnalysis.getLoopDepth(bb);
            double weight = Math.pow(10, depth);
            for (Instruction inst : bb.getInstructions()) {
                for (int i = 0; i < inst.getNumOperands(); i++) {
                    Value op = inst.getOperand(i);
                    if (op instanceof GlobalVariable gv) {
                        scores.put(gv, scores.getOrDefault(gv, 0.0) + weight);
                    }
                }
            }
        }
        // Filter out globals with low usage score
        scores.entrySet().removeIf(entry -> entry.getValue() < 5.0);

        List<GlobalVariable> sorted = new ArrayList<>(scores.keySet());
        sorted.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        return sorted;
    }

    private record MulTerm(int shift, boolean positive) {
    }
}
