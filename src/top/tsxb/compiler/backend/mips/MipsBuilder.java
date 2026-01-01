package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.ir.structure.*;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.base.*;
import top.tsxb.compiler.ir.constant.*;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.*;

import java.util.*;

public class MipsBuilder {
    private final Module module;
    private final StringBuilder sb = new StringBuilder();
    private StringBuilder currentSb = sb;
    private final StringBuilder pendingBridges = new StringBuilder();
    private final Map<Value, Integer> stackOffsets = new LinkedHashMap<>();
    private Map<Value, MipsRegister> regMapping = new LinkedHashMap<>();
    private Map<Value, LiveInterval> intervals = new LinkedHashMap<>();
    private Map<Instruction, Integer> instToId = new LinkedHashMap<>();
    private Set<MipsRegister> usedCalleeSaved = new LinkedHashSet<>();
    private int currentStackSize;
    private int brCounter = 0;

    public MipsBuilder(Module module) {
        this.module = module;
    }

    public String build() {
        genData();
        genText();
        return sb.toString();
    }

    private void addI(String dest, String src, int imm) {
        if (imm >= -32768 && imm <= 32767) {
            currentSb.append("    addiu ").append(dest).append(", ").append(src).append(", ").append(imm).append("\n");
        } else {
            // Load immediate to $at (assembler temporary) or $t9
            currentSb.append("    li $at, ").append(imm).append("\n");
            currentSb.append("    addu ").append(dest).append(", ").append(src).append(", $at\n");
        }
    }

    private void loadMem(String dest, int offset, String base) {
        if (offset >= -32768 && offset <= 32767) {
            currentSb.append("    lw ").append(dest).append(", ").append(offset).append("(").append(base).append(")\n");
        } else {
            currentSb.append("    li $at, ").append(offset).append("\n");
            currentSb.append("    addu $at, ").append(base).append(", $at\n");
            currentSb.append("    lw ").append(dest).append(", 0($at)\n");
        }
    }

    private void storeMem(String src, int offset, String base) {
        if (offset >= -32768 && offset <= 32767) {
            currentSb.append("    sw ").append(src).append(", ").append(offset).append("(").append(base).append(")\n");
        } else {
            currentSb.append("    li $at, ").append(offset).append("\n");
            currentSb.append("    addu $at, ").append(base).append(", $at\n");
            currentSb.append("    sw ").append(src).append(", 0($at)\n");
        }
    }

    private String getLabel(Value val) {
        if (val instanceof BasicBlock bb) {
            return bb.getParent().getName() + "_" + bb.getName().replace(".", "_");
        }
        return val.getName().replace(".", "_");
    }

    private void genData() {
        currentSb.append(".data\n");
        for (GlobalVariable gv : module.getGlobalList()) {
            currentSb.append(".align 2\n");
            currentSb.append(getLabel(gv)).append(": ");
            if (gv.isDeclaration()) {
                IrType type = ((PtrType) gv.getType()).getPointeeType();
                currentSb.append(".space ").append(getSize(type)).append("\n");
            } else {
                Constant init = (Constant) gv.getOperand(0);
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
            currentSb.append(".asciiz \"").append(cs.getContent().replace("\n", "\\n").replace("\"", "\\\"")).append("\"\n");
        }
    }

    private boolean isAllZero(Constant constant) {
        if (constant instanceof ConstInt ci) {
            return ci.getValue() == 0;
        } else if (constant instanceof ConstArray ca) {
            for (Constant val : ca.getValues()) {
                if (!isAllZero(val)) return false;
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

    private List<BasicBlock> reorderBlocks(Function func) {
        List<BasicBlock> original = func.getBasicBlocks();
        if (original.isEmpty()) return original;

        List<BasicBlock> reordered = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();

        BasicBlock current = original.get(0);
        reordered.add(current);
        visited.add(current);

        while (reordered.size() < original.size()) {
            Instruction lastInst = current.getInstructions().get(current.getInstructions().size() - 1);
            BasicBlock next = null;
            if (lastInst instanceof BrInst br) {
                if (br.getNumOperands() == 1) {
                    BasicBlock target = (BasicBlock) br.getOperand(0);
                    if (!visited.contains(target)) {
                        next = target;
                    }
                } else {
                    BasicBlock targetTrue = (BasicBlock) br.getOperand(1);
                    BasicBlock targetFalse = (BasicBlock) br.getOperand(2);
                    // Heuristic: prefer targetTrue (often the loop body or then-branch)
                    if (!visited.contains(targetTrue)) {
                        next = targetTrue;
                    } else if (!visited.contains(targetFalse)) {
                        next = targetFalse;
                    }
                }
            }

            if (next == null) {
                // Find first unvisited block
                for (BasicBlock bb : original) {
                    if (!visited.contains(bb)) {
                        next = bb;
                        break;
                    }
                }
            }

            if (next != null) {
                reordered.add(next);
                visited.add(next);
                current = next;
            } else {
                break;
            }
        }
        return reordered;
    }

    private void genFunction(Function func) {
        currentSb.append(getLabel(func)).append(":\n");

        // Register Allocation
        LivenessAnalysis liveness = new LivenessAnalysis(func);
        liveness.analyze();
        LoopAnalysis loopAnalysis = new LoopAnalysis(func);
        loopAnalysis.analyze();
        LiveIntervalAnalysis intervalAnalysis = new LiveIntervalAnalysis(func, liveness, loopAnalysis);
        intervalAnalysis.analyze();
        this.intervals = intervalAnalysis.getIntervalMap();
        this.instToId = intervalAnalysis.getInstToId();
        LinearScanAllocator allocator = new LinearScanAllocator(intervalAnalysis.getIntervals());
        allocator.allocate();
        this.regMapping = allocator.getRegMapping();
        this.usedCalleeSaved = allocator.getUsedCalleeSaved();

        calculateStackFrame(func);

        currentSb.append("    # Prologue\n");
        addI("$sp", "$sp", -currentStackSize);
        storeMem("$ra", currentStackSize - 4, "$sp");
        storeMem("$fp", currentStackSize - 8, "$sp");
        
        // Save callee-saved registers
        int regOffset = currentStackSize - 12;
        for (MipsRegister reg : usedCalleeSaved) {
            storeMem(reg.getName(), regOffset, "$sp");
            regOffset -= 4;
        }
        
        addI("$fp", "$sp", currentStackSize);

        List<Argument> args = func.getArguments();
        for (int i = 0; i < args.size(); i++) {
            Argument arg = args.get(i);
            MipsRegister reg = regMapping.get(arg);
            if (reg != null) {
                if (i < 4) {
                    currentSb.append("    move ").append(reg.getName()).append(", $a").append(i).append("\n");
                } else {
                    loadMem(reg.getName(), (i - 4) * 4, "$fp");
                }
            } else {
                // Spilled or not used
                Integer offset = stackOffsets.get(arg);
                if (offset != null) {
                    if (i < 4) {
                        storeMem("$a" + i, offset, "$fp");
                    } else {
                        loadMem("$t0", (i - 4) * 4, "$fp");
                        storeMem("$t0", offset, "$fp");
                    }
                }
            }
        }

        List<BasicBlock> blocks = reorderBlocks(func);
        for (int i = 0; i < blocks.size(); i++) {
            BasicBlock bb = blocks.get(i);
            BasicBlock nextBb = (i + 1 < blocks.size()) ? blocks.get(i + 1) : null;
            currentSb.append(getLabel(bb)).append(":\n");
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

    private void calculateStackFrame(Function func) {
        stackOffsets.clear();
        int offset = -8; // -4: ra, -8: fp
        
        // Space for callee-saved registers
        offset -= usedCalleeSaved.size() * 4;

        for (Argument arg : func.getArguments()) {
            if (!regMapping.containsKey(arg)) {
                stackOffsets.put(arg, offset);
                offset -= 4;
            }
        }

        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca) {
                    int size = getSize(((PtrType) alloca.getType()).getPointeeType());
                    size = (size + 3) / 4 * 4; // Align to 4 bytes
                    offset -= size;
                    stackOffsets.put(inst, offset);
                } else if (!(inst.getType() instanceof NoneType) && !regMapping.containsKey(inst)) {
                    offset -= 4;
                    stackOffsets.put(inst, offset);
                }
            }
        }

        currentStackSize = -offset;
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
        currentSb.append("    # ").append(inst.toString()).append("\n");
        switch (inst.getOpCode()) {
            case ADD, SUB, MUL, SDIV, SREM -> genBinary(inst);
            case ICMP -> genIcmp((IcmpInst) inst);
            case ALLOCA -> genAlloca();
            case LOAD -> genLoad((LoadInst) inst);
            case STORE -> genStore((StoreInst) inst);
            case BR -> genBr((BrInst) inst, nextBb);
            case RET -> genRet((ReturnInst) inst);
            case CALL -> genCall((CallInst) inst);
            case ZEXT -> genZext((ZextInst) inst);
            case GEP -> genGep((GetElementPtrInst) inst);
            case PHI -> {
            } // Handled by predecessors
        }
    }

    private void loadValue(Value val, String reg) {
        if (val instanceof ConstInt ci) {
            currentSb.append("    li ").append(reg).append(", ").append(ci.getValue()).append("\n");
        } else if (val instanceof GlobalVariable gv) {
            currentSb.append("    la ").append(reg).append(", ").append(getLabel(gv)).append("\n");
        } else if (val instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            addI(reg, "$fp", dataOffset);
        } else if (regMapping.containsKey(val)) {
            MipsRegister srcReg = regMapping.get(val);
            if (!srcReg.getName().equals(reg)) {
                currentSb.append("    move ").append(reg).append(", ").append(srcReg.getName()).append("\n");
            }
        } else {
            Integer offset = stackOffsets.get(val);
            if (offset == null) {
                if (val instanceof GlobalValue gv) {
                    currentSb.append("    la ").append(reg).append(", ").append(getLabel(gv)).append("\n");
                    return;
                }
                throw new RuntimeException("Value not found in stack or register: " + val);
            }
            loadMem(reg, offset, "$fp");
        }
    }

    private void storeValue(Value inst, String reg) {
        if (regMapping.containsKey(inst)) {
            MipsRegister destReg = regMapping.get(inst);
            if (!destReg.getName().equals(reg)) {
                currentSb.append("    move ").append(destReg.getName()).append(", ").append(reg).append("\n");
            }
        } else {
            Integer offset = stackOffsets.get(inst);
            if (offset != null) {
                storeMem(reg, offset, "$fp");
            }
        }
    }

    private void genBinary(Instruction inst) {
        Value op1 = inst.getOperand(0);
        Value op2 = inst.getOperand(1);

        if (inst.getOpCode() == OpCode.SDIV && op2 instanceof ConstInt ci) {
            if (emitConstDivision(op1, ci.getValue())) {
                storeValue(inst, "$t2");
                return;
            }
        }

        if (inst.getOpCode() == OpCode.SREM && op2 instanceof ConstInt ci) {
            if (emitConstDivision(op1, ci.getValue())) {
                loadValue(op1, "$t0");
                currentSb.append("    li $t3, ").append(ci.getValue()).append("\n");
                currentSb.append("    mul $t1, $t2, $t3\n");
                currentSb.append("    subu $t2, $t0, $t1\n");
                storeValue(inst, "$t2");
                return;
            }
        }

        if (inst.getOpCode() == OpCode.MUL) {
            if (op2 instanceof ConstInt ci && tryConstMul(inst, op1, ci.getValue())) {
                return;
            }
            if (op1 instanceof ConstInt ci && tryConstMul(inst, op2, ci.getValue())) {
                return;
            }
        }

        if (inst.getOpCode() == OpCode.ADD && op2 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            loadValue(op1, "$t0");
            addI("$t2", "$t0", ci.getValue()); // Use helper that handles neg/pos
        } else if (inst.getOpCode() == OpCode.ADD && op1 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            loadValue(op2, "$t0");
            addI("$t2", "$t0", ci.getValue());
        } else if (inst.getOpCode() == OpCode.SUB && op2 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            loadValue(op1, "$t0");
            addI("$t2", "$t0", -ci.getValue());
        } else {
            loadValue(op1, "$t0");
            loadValue(op2, "$t1");
            switch (inst.getOpCode()) {
                case ADD -> currentSb.append("    addu $t2, $t0, $t1\n");
                case SUB -> currentSb.append("    subu $t2, $t0, $t1\n");
                case MUL -> currentSb.append("    mul $t2, $t0, $t1\n");
                case SDIV -> {
                    String okLabel = "div_ok_" + (brCounter++);
                    currentSb.append("    bne $t1, $zero, ").append(okLabel).append("\n");
                    currentSb.append("    break 7\n");
                    currentSb.append(okLabel).append(":\n");
                    currentSb.append("    div $t0, $t1\n    mflo $t2\n");
                }
                case SREM -> {
                    String okLabel = "rem_ok_" + (brCounter++);
                    currentSb.append("    bne $t1, $zero, ").append(okLabel).append("\n");
                    currentSb.append("    break 7\n");
                    currentSb.append(okLabel).append(":\n");
                    currentSb.append("    div $t0, $t1\n    mfhi $t2\n");
                }
            }
        }
        storeValue(inst, "$t2");
    }

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private int log2(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    private record MulTerm(int shift, boolean positive) {
    }

    private boolean tryConstMul(Instruction inst, Value multiplicand, int constant) {
        if (constant == 0) {
            currentSb.append("    addu $t2, $zero, $zero\n");
            storeValue(inst, "$t2");
            return true;
        }

        long absConst = Math.abs((long) constant);
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
        emitMulTerm("$t2", "$t0", firstTerm);
        if (!firstTerm.positive) {
            currentSb.append("    subu $t2, $zero, $t2\n");
        }
        for (MulTerm term : remaining) {
            if (term.shift == 0) {
                if (term.positive) {
                    currentSb.append("    addu $t2, $t2, $t0\n");
                } else {
                    currentSb.append("    subu $t2, $t2, $t0\n");
                }
            } else {
                currentSb.append("    sll $t1, $t0, ").append(term.shift).append("\n");
                if (term.positive) {
                    currentSb.append("    addu $t2, $t2, $t1\n");
                } else {
                    currentSb.append("    subu $t2, $t2, $t1\n");
                }
            }
        }
        if (constant < 0) {
            currentSb.append("    subu $t2, $zero, $t2\n");
        }
        storeValue(inst, "$t2");
        return true;
    }

    private boolean emitConstDivision(Value dividend, int divisor) {
        if (divisor == 0) {
            return false;
        }
        if (divisor == 1) {
            loadValue(dividend, "$t0");
            currentSb.append("    addu $t2, $t0, $zero\n");
            return true;
        }
        if (divisor == -1) {
            loadValue(dividend, "$t0");
            currentSb.append("    subu $t2, $zero, $t0\n");
            return true;
        }
        long absDiv = Math.abs((long) divisor);
        if ((absDiv & absDiv - 1) == 0) {
            int shift = Long.numberOfTrailingZeros(absDiv);
            if (shift > 0) {
                loadValue(dividend, "$t0");
                currentSb.append("    sra $t1, $t0, 31\n");
                currentSb.append("    srl $t1, $t1, ").append(32 - shift).append("\n");
                currentSb.append("    addu $t0, $t0, $t1\n");
                currentSb.append("    sra $t2, $t0, ").append(shift).append("\n");
                if (divisor < 0) {
                    currentSb.append("    subu $t2, $zero, $t2\n");
                }
                return true;
            }
        }
        DivOptimizer.MultiplierInfo info = DivOptimizer.chooseMultiplier(divisor);
        loadValue(dividend, "$t0");
        int magic = (int) info.multiplier();
        currentSb.append("    li $t1, ").append(magic).append("\n");
        currentSb.append("    mult $t0, $t1\n");
        currentSb.append("    mfhi $t2\n");
        if (magic < 0) {
            currentSb.append("    addu $t2, $t2, $t0\n");
        }
        if (info.shift() > 0) {
            currentSb.append("    sra $t2, $t2, ").append(info.shift()).append("\n");
        }
        currentSb.append("    srl $t3, $t0, 31\n");
        currentSb.append("    addu $t2, $t2, $t3\n");
        if (divisor < 0) {
            currentSb.append("    subu $t2, $zero, $t2\n");
        }
        return true;
    }

    private void emitMulTerm(String dest, String source, MulTerm term) {
        if (term.shift == 0) {
            currentSb.append("    addu ").append(dest).append(", ").append(source).append(", $zero\n");
        } else {
            currentSb.append("    sll ").append(dest).append(", ").append(source).append(", ").append(term.shift).append("\n");
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

    private void genIcmp(IcmpInst inst) {
        loadValue(inst.getOperand(0), "$t0");
        loadValue(inst.getOperand(1), "$t1");
        String cond = inst.getPredicate().toString();
        switch (cond) {
            case "eq" -> currentSb.append("    seq $t2, $t0, $t1\n");
            case "ne" -> currentSb.append("    sne $t2, $t0, $t1\n");
            case "sgt" -> currentSb.append("    sgt $t2, $t0, $t1\n");
            case "sge" -> currentSb.append("    sge $t2, $t0, $t1\n");
            case "slt" -> currentSb.append("    slt $t2, $t0, $t1\n");
            case "sle" -> currentSb.append("    sle $t2, $t0, $t1\n");
        }
        storeValue(inst, "$t2");
    }

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

    private void genLoad(LoadInst inst) {
        Value addr = inst.getOperand(0);
        if (addr instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            loadMem("$t1", dataOffset, "$fp");
        } else if (addr instanceof GlobalVariable gv) {
            currentSb.append("    lw $t1, ").append(getLabel(gv)).append("\n");
        } else {
            loadValue(addr, "$t0");
            currentSb.append("    lw $t1, 0($t0)\n");
        }
        storeValue(inst, "$t1");
    }

    private void genStore(StoreInst inst) {
        loadValue(inst.getOperand(0), "$t0"); // Get value
        Value addr = inst.getOperand(1);
        if (addr instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            storeMem("$t0", dataOffset, "$fp");
        } else if (addr instanceof GlobalVariable gv) {
            currentSb.append("    sw $t0, ").append(getLabel(gv)).append("\n");
        } else {
            loadValue(addr, "$t1");
            currentSb.append("    sw $t0, 0($t1)\n");
        }
    }

    private void genBr(BrInst inst, BasicBlock nextBb) {
        if (inst.getNumOperands() == 1) {
            BasicBlock target = (BasicBlock) inst.getOperand(0);
            fillPhis(inst.getParent(), target);
            if (target != nextBb) {
                currentSb.append("    j ").append(getLabel(target)).append("\n");
            }
        } else {
            Value cond = inst.getOperand(0);
            BasicBlock targetTrue = (BasicBlock) inst.getOperand(1);
            BasicBlock targetFalse = (BasicBlock) inst.getOperand(2);

            loadValue(cond, "$t0");
            String labelTrue = getLabel(targetTrue);
            String labelFalse = getLabel(targetFalse);

            boolean phisTrue = hasPhis(inst.getParent(), targetTrue);
            boolean phisFalse = hasPhis(inst.getParent(), targetFalse);

            if (targetFalse == nextBb && !phisFalse) {
                // Fall through to False
                if (!phisTrue) {
                    currentSb.append("    bne $t0, $zero, ").append(labelTrue).append("\n");
                } else {
                    String bridgeLabel = "br_bridge_" + (brCounter++);
                    currentSb.append("    bne $t0, $zero, ").append(bridgeLabel).append("\n");

                    StringBuilder oldSb = currentSb;
                    currentSb = pendingBridges;
                    currentSb.append(bridgeLabel).append(":\n");
                    fillPhis(inst.getParent(), targetTrue);
                    currentSb.append("    j ").append(labelTrue).append("\n");
                    currentSb = oldSb;
                }
            } else if (targetTrue == nextBb && !phisTrue) {
                // Fall through to True
                if (!phisFalse) {
                    currentSb.append("    beq $t0, $zero, ").append(labelFalse).append("\n");
                } else {
                    String bridgeLabel = "br_bridge_" + (brCounter++);
                    currentSb.append("    beq $t0, $zero, ").append(bridgeLabel).append("\n");

                    StringBuilder oldSb = currentSb;
                    currentSb = pendingBridges;
                    currentSb.append(bridgeLabel).append(":\n");
                    fillPhis(inst.getParent(), targetFalse);
                    currentSb.append("    j ").append(labelFalse).append("\n");
                    currentSb = oldSb;
                }
            } else {
                // Neither is next or both have phis
                if (!phisFalse) {
                    currentSb.append("    beq $t0, $zero, ").append(labelFalse).append("\n");
                } else {
                    String bridgeLabel = "br_bridge_" + (brCounter++);
                    currentSb.append("    beq $t0, $zero, ").append(bridgeLabel).append("\n");

                    StringBuilder oldSb = currentSb;
                    currentSb = pendingBridges;
                    currentSb.append(bridgeLabel).append(":\n");
                    fillPhis(inst.getParent(), targetFalse);
                    currentSb.append("    j ").append(labelFalse).append("\n");
                    currentSb = oldSb;
                }

                fillPhis(inst.getParent(), targetTrue);
                if (targetTrue != nextBb) {
                    currentSb.append("    j ").append(labelTrue).append("\n");
                }
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

        Map<PhiInst, Value> assignments = new LinkedHashMap<>();
        Map<PhiInst, Integer> useCount = new HashMap<>();
        Set<PhiInst> phiSet = new HashSet<>(phis);

        for (PhiInst phi : phis) {
            Value incoming = phi.getIncomingValue(current);
            if (incoming != null && incoming != phi) {
                assignments.put(phi, incoming);
                if (incoming instanceof PhiInst incomingPhi && phiSet.contains(incomingPhi)) {
                    useCount.put(incomingPhi, useCount.getOrDefault(incomingPhi, 0) + 1);
                }
            }
        }

        Queue<PhiInst> ready = new LinkedList<>();
        for (PhiInst phi : assignments.keySet()) {
            if (useCount.getOrDefault(phi, 0) == 0) {
                ready.add(phi);
            }
        }

        while (!ready.isEmpty()) {
            PhiInst phi = ready.poll();
            Value incoming = assignments.get(phi);
            loadValue(incoming, "$t0");
            storeValue(phi, "$t0");
            assignments.remove(phi);

            if (incoming instanceof PhiInst incomingPhi && assignments.containsKey(incomingPhi)) {
                useCount.put(incomingPhi, useCount.get(incomingPhi) - 1);
                if (useCount.get(incomingPhi) == 0) {
                    ready.add(incomingPhi);
                }
            }
        }

        if (!assignments.isEmpty()) {
            List<PhiInst> cyclePhis = new ArrayList<>(assignments.keySet());
            int tempSpace = cyclePhis.size() * 4;
            addI("$sp", "$sp", -tempSpace);

            for (int i = 0; i < cyclePhis.size(); i++) {
                PhiInst phi = cyclePhis.get(i);
                Value incoming = assignments.get(phi);
                loadValue(incoming, "$t0");
                storeMem("$t0", i * 4, "$sp");
            }

            for (int i = 0; i < cyclePhis.size(); i++) {
                PhiInst phi = cyclePhis.get(i);
                loadMem("$t0", i * 4, "$sp");
                storeValue(phi, "$t0");
            }

            addI("$sp", "$sp", tempSpace);
        }
    }

    private void genRet(ReturnInst inst) {
        if (inst.getNumOperands() > 0) {
            loadValue(inst.getOperand(0), "$v0");
        }
        
        // Restore callee-saved registers
        int regOffset = currentStackSize - 12;
        for (MipsRegister reg : usedCalleeSaved) {
            loadMem(reg.getName(), regOffset, "$sp");
            regOffset -= 4;
        }
        
        loadMem("$ra", currentStackSize - 4, "$sp");
        loadMem("$fp", currentStackSize - 8, "$sp");
        addI("$sp", "$sp", currentStackSize);
        currentSb.append("    jr $ra\n");
    }

    private void genCall(CallInst inst) {
        Function target = (Function) inst.getOperand(0);
        int numArgs = inst.getNumOperands() - 1;
        
        // Save caller-saved registers that are live across this call
        int instId = instToId.get(inst);
        List<MipsRegister> toSave = new ArrayList<>();
        for (Map.Entry<Value, MipsRegister> entry : regMapping.entrySet()) {
            MipsRegister reg = entry.getValue();
            if (reg.isCallerSaved()) {
                LiveInterval interval = intervals.get(entry.getKey());
                if (interval != null && interval.getStart() < instId && interval.getEnd() > instId) {
                    toSave.add(reg);
                }
            }
        }

        // Save to stack (below current sp)
        if (!toSave.isEmpty()) {
            addI("$sp", "$sp", -toSave.size() * 4);
            for (int i = 0; i < toSave.size(); i++) {
                storeMem(toSave.get(i).getName(), i * 4, "$sp");
            }
        }

        int stackArgs = Math.max(0, numArgs - 4);
        int stackSpace = (stackArgs * 4 + 7) / 8 * 8;

        if (stackSpace > 0) {
            addI("$sp", "$sp", -stackSpace);
        }

        for (int i = 1; i < inst.getNumOperands(); i++) {
            Value arg = inst.getOperand(i);
            if (i - 1 < 4) {
                loadValue(arg, "$a" + (i - 1));
            } else {
                loadValue(arg, "$t0");
                int offset = (i - 1 - 4) * 4;
                storeMem("$t0", offset, "$sp");
            }
        }
        currentSb.append("    jal ").append(getLabel(target)).append("\n");

        if (stackSpace > 0) {
            addI("$sp", "$sp", stackSpace);
        }

        // Restore caller-saved registers
        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i++) {
                loadMem(toSave.get(i).getName(), i * 4, "$sp");
            }
            addI("$sp", "$sp", toSave.size() * 4);
        }

        if (!(inst.getType() instanceof NoneType)) {
            storeValue(inst, "$v0");
        }
    }

    private void genZext(ZextInst inst) {
        loadValue(inst.getOperand(0), "$t0");
        storeValue(inst, "$t0");
    }

    private void genGep(GetElementPtrInst inst) {
        loadValue(inst.getOperand(0), "$t0");

        IrType currentType = ((PtrType) inst.getOperand(0).getType()).getPointeeType();

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
                    addI("$t0", "$t0", offset);
                }
            } else {
                loadValue(index, "$t1");
                if (elementSize == 1) {
                    currentSb.append("    addu $t0, $t0, $t1\n");
                } else if (isPowerOfTwo(elementSize)) {
                    currentSb.append("    sll $t1, $t1, ").append(log2(elementSize)).append("\n");
                    currentSb.append("    addu $t0, $t0, $t1\n");
                } else {
                    currentSb.append("    li $t2, ").append(elementSize).append("\n");
                    currentSb.append("    mul $t1, $t1, $t2\n");
                    currentSb.append("    addu $t0, $t0, $t1\n");
                }
            }
        }
        storeValue(inst, "$t0");
    }
}
