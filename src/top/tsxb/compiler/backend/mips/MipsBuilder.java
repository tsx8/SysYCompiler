package top.tsxb.compiler.backend.mips;

import top.tsxb.compiler.ir.structure.*;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.base.*;
import top.tsxb.compiler.ir.constant.*;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MipsBuilder {
    private final Module module;
    private final StringBuilder sb = new StringBuilder();
    private final Map<Value, Integer> stackOffsets = new HashMap<>();
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
            sb.append("    addiu ").append(dest).append(", ").append(src).append(", ").append(imm).append("\n");
        } else {
            // Load immediate to $at (assembler temporary) or $t9
            sb.append("    li $at, ").append(imm).append("\n");
            sb.append("    addu ").append(dest).append(", ").append(src).append(", $at\n");
        }
    }

    private void loadMem(String dest, int offset, String base) {
        if (offset >= -32768 && offset <= 32767) {
            sb.append("    lw ").append(dest).append(", ").append(offset).append("(").append(base).append(")\n");
        } else {
            sb.append("    li $at, ").append(offset).append("\n");
            sb.append("    addu $at, ").append(base).append(", $at\n");
            sb.append("    lw ").append(dest).append(", 0($at)\n");
        }
    }

    private void storeMem(String src, int offset, String base) {
        if (offset >= -32768 && offset <= 32767) {
            sb.append("    sw ").append(src).append(", ").append(offset).append("(").append(base).append(")\n");
        } else {
            sb.append("    li $at, ").append(offset).append("\n");
            sb.append("    addu $at, ").append(base).append(", $at\n");
            sb.append("    sw ").append(src).append(", 0($at)\n");
        }
    }

    private String getLabel(Value val) {
        if (val instanceof BasicBlock bb) {
            return bb.getParent().getName() + "_" + bb.getName().replace(".", "_");
        }
        return val.getName().replace(".", "_");
    }

    private void genData() {
        sb.append(".data\n");
        for (GlobalVariable gv : module.getGlobalList()) {
            sb.append(".align 2\n");
            sb.append(getLabel(gv)).append(": ");
            if (gv.isDeclaration()) {
                IrType type = ((PtrType) gv.getType()).getPointeeType();
                sb.append(".space ").append(getSize(type)).append("\n");
            } else {
                Constant init = (Constant) gv.getOperand(0);
                genConstant(init);
            }
        }
    }

    private void genConstant(Constant constant) {
        if (constant instanceof ConstInt ci) {
            sb.append(".word ").append(ci.getValue()).append("\n");
        } else if (constant instanceof ConstArray ca) {
            if (isAllZero(ca)) {
                sb.append(".space ").append(getSize(ca.getType())).append("\n");
            } else {
                for (Constant val : ca.getValues()) {
                    genConstant(val);
                }
            }
        } else if (constant instanceof ConstZero cz) {
            int size = getSize(cz.getType());
            sb.append(".space ").append(size).append("\n");
        } else if (constant instanceof ConstString cs) {
            sb.append(".asciiz \"").append(cs.getContent().replace("\n", "\\n").replace("\"", "\\\"")).append("\"\n");
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
        sb.append(".text\n");
        sb.append("    jal main\n");
        sb.append("    li $v0, 10\n");
        sb.append("    syscall\n\n");
        for (Function func : module.getFunctionList()) {
            if (!func.isDeclaration()) {
                genFunction(func);
            }
        }
        genRuntime();
    }

    private void genRuntime() {
        sb.append("\n# SysY Runtime\n");
        sb.append("getint:\n    li $v0, 5\n    syscall\n    jr $ra\n");
        sb.append("putint:\n    li $v0, 1\n    syscall\n    jr $ra\n");
        sb.append("putch:\n    li $v0, 11\n    syscall\n    jr $ra\n");
        sb.append("putstr:\n    li $v0, 4\n    syscall\n    jr $ra\n");
    }

    private void genFunction(Function func) {
        sb.append(getLabel(func)).append(":\n");

        calculateStackFrame(func);

        sb.append("    # Prologue\n");
        addI("$sp", "$sp", -currentStackSize);
        storeMem("$ra", currentStackSize - 4, "$sp");
        storeMem("$fp", currentStackSize - 8, "$sp");
        addI("$fp", "$sp", currentStackSize);

        List<Argument> args = func.getArguments();
        for (int i = 0; i < args.size(); i++) {
            Argument arg = args.get(i);
            int offset = stackOffsets.get(arg);
            if (i < 4) {
                storeMem("$a" + i, offset, "$fp");
            } else {
                int callerOffset = (i - 4) * 4;
                loadMem("$t0", callerOffset, "$fp");
                storeMem("$t0", offset, "$fp");
            }
        }

        for (BasicBlock bb : func.getBasicBlocks()) {
            sb.append(getLabel(bb)).append(":\n");
            for (Instruction inst : bb.getInstructions()) {
                genInstruction(inst);
            }
        }
        sb.append("\n");
    }

    private void calculateStackFrame(Function func) {
        stackOffsets.clear();
        int offset = -12; // -4: ra, -8: fp

        for (Argument arg : func.getArguments()) {
            stackOffsets.put(arg, offset);
            offset -= 4;
        }

        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca) {
                    int size = getSize(((PtrType) alloca.getType()).getPointeeType());
                    size = (size + 3) / 4 * 4; // Align to 4 bytes
                    offset -= size;
                    stackOffsets.put(inst, offset);
                } else if (!(inst.getType() instanceof NoneType)) {
                    offset -= 4;
                    stackOffsets.put(inst, offset);
                }
            }
        }

        currentStackSize = -offset;
        currentStackSize = (currentStackSize + 7) / 8 * 8;
    }

    private void genInstruction(Instruction inst) {
        sb.append("    # ").append(inst.toString()).append("\n");
        switch (inst.getOpCode()) {
            case ADD, SUB, MUL, SDIV, SREM -> genBinary(inst);
            case ICMP -> genIcmp((IcmpInst) inst);
            case ALLOCA -> genAlloca();
            case LOAD -> genLoad((LoadInst) inst);
            case STORE -> genStore((StoreInst) inst);
            case BR -> genBr((BrInst) inst);
            case RET -> genRet((ReturnInst) inst);
            case CALL -> genCall((CallInst) inst);
            case ZEXT -> genZext((ZextInst) inst);
            case GEP -> genGep((GetElementPtrInst) inst);
        }
    }

    private void loadValue(Value val, String reg) {
        if (val instanceof ConstInt ci) {
            sb.append("    li ").append(reg).append(", ").append(ci.getValue()).append("\n");
        } else if (val instanceof GlobalVariable gv) {
            sb.append("    la ").append(reg).append(", ").append(getLabel(gv)).append("\n");
        } else if (val instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            addI(reg, "$fp", dataOffset);
        } else {
            Integer offset = stackOffsets.get(val);
            if (offset == null) {
                if (val instanceof GlobalValue gv) {
                    sb.append("    la ").append(reg).append(", ").append(getLabel(gv)).append("\n");
                    return;
                }
                throw new RuntimeException("Value not found in stack: " + val);
            }
            loadMem(reg, offset, "$fp");
        }
    }

    private void storeValue(Value inst, String reg) {
        Integer offset = stackOffsets.get(inst);
        if (offset != null) {
            storeMem(reg, offset, "$fp");
        }
    }

    private void genBinary(Instruction inst) {
        Value op1 = inst.getOperand(0);
        Value op2 = inst.getOperand(1);

        if (inst.getOpCode() == OpCode.ADD && op2 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            loadValue(op1, "$t0");
            addI("$t2", "$t0", ci.getValue()); // Use helper that handles neg/pos
        } else if (inst.getOpCode() == OpCode.ADD && op1 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            loadValue(op2, "$t0");
            addI("$t2", "$t0", ci.getValue());
        } else if (inst.getOpCode() == OpCode.SUB && op2 instanceof ConstInt ci && Math.abs(ci.getValue()) < 32768) {
            loadValue(op1, "$t0");
            addI("$t2", "$t0", -ci.getValue());
        } else if (inst.getOpCode() == OpCode.MUL && op2 instanceof ConstInt ci && isPowerOfTwo(ci.getValue())) {
            loadValue(op1, "$t0");
            sb.append("    sll $t2, $t0, ").append(log2(ci.getValue())).append("\n");
        } else if (inst.getOpCode() == OpCode.MUL && op1 instanceof ConstInt ci && isPowerOfTwo(ci.getValue())) {
            loadValue(op2, "$t0");
            sb.append("    sll $t2, $t0, ").append(log2(ci.getValue())).append("\n");
        } else {
            loadValue(op1, "$t0");
            loadValue(op2, "$t1");
            switch (inst.getOpCode()) {
                case ADD -> sb.append("    addu $t2, $t0, $t1\n");
                case SUB -> sb.append("    subu $t2, $t0, $t1\n");
                case MUL -> sb.append("    mul $t2, $t0, $t1\n");
                case SDIV -> {
                    String okLabel = "div_ok_" + (brCounter++);
                    sb.append("    bne $t1, $zero, ").append(okLabel).append("\n");
                    sb.append("    break 7\n");
                    sb.append(okLabel).append(":\n");
                    sb.append("    div $t0, $t1\n    mflo $t2\n");
                }
                case SREM -> {
                    String okLabel = "rem_ok_" + (brCounter++);
                    sb.append("    bne $t1, $zero, ").append(okLabel).append("\n");
                    sb.append("    break 7\n");
                    sb.append(okLabel).append(":\n");
                    sb.append("    div $t0, $t1\n    mfhi $t2\n");
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

    private void genIcmp(IcmpInst inst) {
        loadValue(inst.getOperand(0), "$t0");
        loadValue(inst.getOperand(1), "$t1");
        String cond = inst.getPredicate().toString();
        switch (cond) {
            case "eq" -> sb.append("    seq $t2, $t0, $t1\n");
            case "ne" -> sb.append("    sne $t2, $t0, $t1\n");
            case "sgt" -> sb.append("    sgt $t2, $t0, $t1\n");
            case "sge" -> sb.append("    sge $t2, $t0, $t1\n");
            case "slt" -> sb.append("    slt $t2, $t0, $t1\n");
            case "sle" -> sb.append("    sle $t2, $t0, $t1\n");
        }
        storeValue(inst, "$t2");
    }

    private void genAlloca() {
        // Space reserved in calculateStackFrame
    }

    private int getAllocaDataOffset(AllocaInst alloca) {
        return stackOffsets.get(alloca);
    }

    private void genLoad(LoadInst inst) {
        Value addr = inst.getOperand(0);
        if (addr instanceof AllocaInst alloca) {
            int dataOffset = getAllocaDataOffset(alloca);
            loadMem("$t1", dataOffset, "$fp");
        } else if (addr instanceof GlobalVariable gv) {
            sb.append("    lw $t1, ").append(getLabel(gv)).append("\n");
        } else {
            loadValue(addr, "$t0");
            sb.append("    lw $t1, 0($t0)\n");
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
            sb.append("    sw $t0, ").append(getLabel(gv)).append("\n");
        } else {
            loadValue(addr, "$t1");
            sb.append("    sw $t0, 0($t1)\n");
        }
    }

    private void genBr(BrInst inst) {
        if (inst.getNumOperands() == 1) {
            sb.append("    j ").append(getLabel(inst.getOperand(0))).append("\n");
        } else {
            loadValue(inst.getOperand(0), "$t0");
            String labelTrue = getLabel(inst.getOperand(1));
            String labelFalse = getLabel(inst.getOperand(2));
            String bridgeLabel = "br_bridge_" + (brCounter++);
            sb.append("    beq $t0, $zero, ").append(bridgeLabel).append("\n");
            sb.append("    j ").append(labelTrue).append("\n");
            sb.append(bridgeLabel).append(":\n");
            sb.append("    j ").append(labelFalse).append("\n");
        }
    }

    private void genRet(ReturnInst inst) {
        if (inst.getNumOperands() > 0) {
            loadValue(inst.getOperand(0), "$v0");
        }
        loadMem("$ra", currentStackSize - 4, "$sp");
        loadMem("$fp", currentStackSize - 8, "$sp");
        addI("$sp", "$sp", currentStackSize);
        sb.append("    jr $ra\n");
    }

    private void genCall(CallInst inst) {
        Function target = (Function) inst.getOperand(0);
        int numArgs = inst.getNumOperands() - 1;
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
        sb.append("    jal ").append(getLabel(target)).append("\n");

        if (stackSpace > 0) {
            addI("$sp", "$sp", stackSpace);
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
                    sb.append("    addu $t0, $t0, $t1\n");
                } else if (isPowerOfTwo(elementSize)) {
                    sb.append("    sll $t1, $t1, ").append(log2(elementSize)).append("\n");
                    sb.append("    addu $t0, $t0, $t1\n");
                } else {
                    sb.append("    li $t2, ").append(elementSize).append("\n");
                    sb.append("    mul $t1, $t1, $t2\n");
                    sb.append("    addu $t0, $t0, $t1\n");
                }
            }
        }
        storeValue(inst, "$t0");
    }
}