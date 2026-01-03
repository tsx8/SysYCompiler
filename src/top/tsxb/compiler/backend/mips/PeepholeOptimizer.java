package top.tsxb.compiler.backend.mips;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PeepholeOptimizer {
    private final List<String> lines;

    private static final Pattern LI_PATTERN = Pattern.compile("^\\s*li\\s+(\\$[a-z0-9]+),\\s+(-?\\d+)\\s*$");
    private static final Pattern MOVE_PATTERN = Pattern.compile("^\\s*move\\s+(\\$[a-z0-9]+),\\s+(\\$[a-z0-9]+)\\s*$");
    private static final Pattern SW_PATTERN = Pattern.compile("^\\s*sw\\s+(\\$[a-z0-9]+),\\s+(-?\\d+\\(\\$[a-z0-9]+\\))\\s*$");
    private static final Pattern BEQ_PATTERN = Pattern.compile("^\\s*beq\\s+(\\$[a-z0-9]+),\\s+(\\$[a-z0-9]+),\\s+([a-zA-Z0-9_]+)\\s*$");
    private static final Pattern BNE_PATTERN = Pattern.compile("^\\s*bne\\s+(\\$[a-z0-9]+),\\s+(\\$[a-z0-9]+),\\s+([a-zA-Z0-9_]+)\\s*$");
    private static final Pattern LW_PATTERN = Pattern.compile("^\\s*lw\\s+(\\$[a-z0-9]+),\\s+(-?\\d+\\(\\$[a-z0-9]+\\))\\s*$");
    private static final Pattern J_PATTERN = Pattern.compile("^\\s*j\\s+([a-zA-Z0-9_]+)\\s*$");
    private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*([a-zA-Z0-9_]+):\\s*$");

    public PeepholeOptimizer(String mipsCode) {
        this.lines = new ArrayList<>(Arrays.asList(mipsCode.split("\n")));
    }

    public String optimize() {
        boolean changed = true;
        int pass = 0;
        while (changed && pass < 10) {
            changed = removeSelfMoves();
            changed |= optimizeLiMove();
            changed |= optimizeMoveMove();
            changed |= optimizeMoveSw();
            changed |= optimizeMoveBranch();
            changed |= optimizeMoveMemAddr();
            changed |= removeRedundantJumps();
            changed |= optimizeBranchInversion();
            changed |= optimizeLoadStoreForwarding();
            pass++;
        }
        return String.join("\n", lines) + "\n";
    }

    /**
     * Remove 'move $reg, $reg'
     */
    private boolean removeSelfMoves() {
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = MOVE_PATTERN.matcher(line);
            if (m.matches()) {
                String dest = m.group(1);
                String src = m.group(2);
                if (dest.equals(src)) {
                    lines.remove(i);
                    i--;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean isSafeToRemove(String reg, int nextIndex) {
        for (int i = nextIndex; i < lines.size() && i < nextIndex + 20; i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.endsWith(":")) return false; // Label - control flow merge, unsafe
            if (line.startsWith("j") || line.startsWith("b")) return false; // Branch/Jump - unsafe

         
            if (line.contains(reg)) {
                
                String[] parts = line.split("[\\s,]+");
                if (parts.length > 1) {
                    String opcode = parts[0];
                    String op1 = parts[1];
                    
                   
                    if (isDestOpcode(opcode)) {
                        if (op1.equals(reg)) {
                            return true;
                        }
                        return false;
                    }

                    if (isSourceOpcode(opcode)) {
                        return false;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private boolean isDestOpcode(String op) {
        return op.equals("add") || op.equals("addu") || op.equals("addiu") ||
               op.equals("sub") || op.equals("subu") ||
               op.equals("mul") || op.equals("div") || // div $d, $s, $t (pseudo) or div $s, $t (native) - wait, native div writes LO/HI
               op.equals("sll") || op.equals("srl") || op.equals("sra") ||
               op.equals("slt") || op.equals("slti") || op.equals("sltiu") || op.equals("sltu") ||
               op.equals("and") || op.equals("or") || op.equals("xor") || op.equals("nor") ||
               op.equals("andi") || op.equals("ori") || op.equals("xori") ||
               op.equals("li") || op.equals("la") ||
               op.equals("lw") || op.equals("lb") || op.equals("lh") ||
               op.equals("move") || op.equals("mfhi") || op.equals("mflo");
    }

    private boolean isSourceOpcode(String op) {
        return op.equals("sw") || op.equals("sb") || op.equals("sh") ||
               op.equals("beq") || op.equals("bne") || op.equals("bgt") || op.equals("bge") ||
               op.equals("blt") || op.equals("ble") || op.equals("bgtz") || op.equals("blez") ||
               op.equals("jr") || op.equals("jalr") ||
               op.equals("mult") || op.equals("multu") || // mult $s, $t
               op.equals("div") || op.equals("divu");     // div $s, $t
    }

    /**
     * Optimize:
     * li $t0, imm
     * move $dst, $t0
     * ->
     * li $dst, imm
     * (Only if $t0 is a temporary register likely used for bridging)
     */
    private boolean optimizeLiMove() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);

            Matcher m1 = LI_PATTERN.matcher(line1);
            Matcher m2 = MOVE_PATTERN.matcher(line2);

            if (m1.matches() && m2.matches()) {
                String tReg = m1.group(1);
                String imm = m1.group(2);
                String dst = m2.group(1);
                String src = m2.group(2);

                if (src.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        String indent = getIndent(line2);
                        String newInst = indent + "li " + dst + ", " + imm;
                        
                        lines.set(i + 1, newInst);
                        lines.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Optimize:
     * move $t0, $src
     * move $dst, $t0
     * ->
     * move $dst, $src
     */
    private boolean optimizeMoveMove() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);

            Matcher m1 = MOVE_PATTERN.matcher(line1);
            Matcher m2 = MOVE_PATTERN.matcher(line2);

            if (m1.matches() && m2.matches()) {
                String tReg = m1.group(1);
                String src1 = m1.group(2);
                String dst2 = m2.group(1);
                String src2 = m2.group(2);

                if (src2.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        String indent = getIndent(line2);
                        String newInst = indent + "move " + dst2 + ", " + src1;
                        
                        lines.set(i + 1, newInst);
                        lines.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Optimize:
     * move $t0, $src
     * sw $t0, offset($base)
     * ->
     * sw $src, offset($base)
     */
    private boolean optimizeMoveSw() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);

            Matcher m1 = MOVE_PATTERN.matcher(line1);
            Matcher m2 = SW_PATTERN.matcher(line2);

            if (m1.matches() && m2.matches()) {
                String tReg = m1.group(1);
                String src1 = m1.group(2);
                String src2 = m2.group(1); // The register being stored
                String addr = m2.group(2);

                if (src2.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        String indent = getIndent(line2);
                        String newInst = indent + "sw " + src1 + ", " + addr;
                        
                        lines.set(i + 1, newInst);
                        lines.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Optimize:
     * move $t0, $src
     * beq $t0, $other, label
     * ->
     * beq $src, $other, label
     * And similarly for bne.
     */
    private boolean optimizeMoveBranch() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);

            Matcher m1 = MOVE_PATTERN.matcher(line1);
            Matcher mBeq = BEQ_PATTERN.matcher(line2);
            Matcher mBne = BNE_PATTERN.matcher(line2);

            if (m1.matches()) {
                String tReg = m1.group(1);
                String src = m1.group(2);
                
                if (mBeq.matches()) {
                    String op1 = mBeq.group(1);
                    String op2 = mBeq.group(2);
                    String label = mBeq.group(3);
                    
                    if (op1.equals(tReg) && isTempReg(tReg)) {
                        if (isSafeToRemove(tReg, i + 2)) {
                            String indent = getIndent(line2);
                            String newInst = indent + "beq " + src + ", " + op2 + ", " + label;
                            lines.set(i + 1, newInst);
                            lines.remove(i);
                            i--;
                            changed = true;
                        }
                    } else if (op2.equals(tReg) && isTempReg(tReg)) {
                        if (isSafeToRemove(tReg, i + 2)) {
                            String indent = getIndent(line2);
                            String newInst = indent + "beq " + op1 + ", " + src + ", " + label;
                            lines.set(i + 1, newInst);
                            lines.remove(i);
                            i--;
                            changed = true;
                        }
                    }
                } else if (mBne.matches()) {
                    String op1 = mBne.group(1);
                    String op2 = mBne.group(2);
                    String label = mBne.group(3);
                    
                    if (op1.equals(tReg) && isTempReg(tReg)) {
                        if (isSafeToRemove(tReg, i + 2)) {
                            String indent = getIndent(line2);
                            String newInst = indent + "bne " + src + ", " + op2 + ", " + label;
                            lines.set(i + 1, newInst);
                            lines.remove(i);
                            i--;
                            changed = true;
                        }
                    } else if (op2.equals(tReg) && isTempReg(tReg)) {
                        if (isSafeToRemove(tReg, i + 2)) {
                            String indent = getIndent(line2);
                            String newInst = indent + "bne " + op1 + ", " + src + ", " + label;
                            lines.set(i + 1, newInst);
                            lines.remove(i);
                            i--;
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Optimize:
     * move $t0, $base
     * sw $src, offset($t0)  OR  lw $dst, offset($t0)
     * ->
     * sw $src, offset($base) OR lw $dst, offset($base)
     */
    private boolean optimizeMoveMemAddr() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);

            Matcher m1 = MOVE_PATTERN.matcher(line1);
            Matcher mLw = LW_PATTERN.matcher(line2);
            Matcher mSw = SW_PATTERN.matcher(line2);
            
            Matcher mMem = null;
            boolean isStore = false;
            if (mLw.matches()) {
                mMem = mLw;
            } else if (mSw.matches()) {
                mMem = mSw;
                isStore = true;
            }

            if (m1.matches() && mMem != null) {
                String tReg = m1.group(1);
                String baseSrc = m1.group(2);
                
                String memSrcDest = mMem.group(1);
                String addr = mMem.group(2);
                
                int parenIndex = addr.indexOf('(');
                int closeParenIndex = addr.indexOf(')');
                if (parenIndex != -1 && closeParenIndex != -1) {
                    String offset = addr.substring(0, parenIndex);
                    String baseReg = addr.substring(parenIndex + 1, closeParenIndex);
                    
                    if (baseReg.equals(tReg) && isTempReg(tReg)) {
                        if (isSafeToRemove(tReg, i + 2)) {
                            String indent = getIndent(line2);
                            String op = isStore ? "sw" : "lw";
                            String newInst = indent + op + " " + memSrcDest + ", " + offset + "(" + baseSrc + ")";
                            lines.set(i + 1, newInst);
                            lines.remove(i);
                            i--;
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean isTempReg(String reg) {
        return reg.matches("\\$t[0-9]");
    }

    private String getIndent(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    /**
     * Remove 'j Label' followed by 'Label:'
     */
    private boolean removeRedundantJumps() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);

            Matcher m1 = J_PATTERN.matcher(line1);
            Matcher m2 = LABEL_PATTERN.matcher(line2);

            if (m1.matches() && m2.matches()) {
                String target = m1.group(1);
                String label = m2.group(1);
                if (target.equals(label)) {
                    lines.remove(i);
                    i--;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Optimize:
     * bne $t0, $zero, L1
     * j L2
     * L1:
     * ->
     * beq $t0, $zero, L2
     * L1:
     */
    private boolean optimizeBranchInversion() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 2; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);
            String line3 = lines.get(i + 2);

            Matcher mBeq = BEQ_PATTERN.matcher(line1);
            Matcher mBne = BNE_PATTERN.matcher(line1);
            Matcher mJ = J_PATTERN.matcher(line2);
            Matcher mLabel = LABEL_PATTERN.matcher(line3);

            if ((mBeq.matches() || mBne.matches()) && mJ.matches() && mLabel.matches()) {
                boolean isBeq = mBeq.matches();
                Matcher mB = isBeq ? mBeq : mBne;

                String reg1 = mB.group(1);
                String reg2 = mB.group(2);
                String target1 = mB.group(3);
                String target2 = mJ.group(1);
                String label = mLabel.group(1);

                if (target1.equals(label)) {
                    String indent = getIndent(line1);
                    String newOp = isBeq ? "bne" : "beq";
                    String newInst = indent + newOp + " " + reg1 + ", " + reg2 + ", " + target2;
                    lines.set(i, newInst);
                    lines.remove(i + 1);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Optimize:
     * sw $t0, 0($sp)
     * lw $t0, 0($sp) -> remove lw
     * OR
     * sw $t0, 0($sp)
     * lw $t1, 0($sp) -> move $t1, $t0
     */
    private boolean optimizeLoadStoreForwarding() {
        boolean changed = false;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line1 = lines.get(i);
            String line2 = lines.get(i + 1);

            Matcher mSw = SW_PATTERN.matcher(line1);
            Matcher mLw = LW_PATTERN.matcher(line2);

            if (mSw.matches() && mLw.matches()) {
                String srcReg = mSw.group(1);
                String addr1 = mSw.group(2);
                String dstReg = mLw.group(1);
                String addr2 = mLw.group(2);

                if (addr1.equals(addr2)) {
                    if (srcReg.equals(dstReg)) {
                        lines.remove(i + 1);
                        changed = true;
                    } else {
                        String indent = getIndent(line2);
                        String newInst = indent + "move " + dstReg + ", " + srcReg;
                        lines.set(i + 1, newInst);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }
}
