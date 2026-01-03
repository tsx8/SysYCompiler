package top.tsxb.compiler.backend.mips;

import java.util.ArrayList;
import java.util.List;

public class PeepholeOptimizer {
    private final List<Inst> insts = new ArrayList<>();

    public PeepholeOptimizer(String mipsCode) {
        for (String line : mipsCode.split("\n")) {
            insts.add(new Inst(line));
        }
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
        StringBuilder sb = new StringBuilder();
        for (Inst inst : insts) {
            sb.append(inst.toString()).append("\n");
        }
        return sb.toString();
    }

    private boolean removeSelfMoves() {
        boolean changed = false;
        for (int i = 0; i < insts.size(); i++) {
            Inst inst = insts.get(i);
            if ("move".equals(inst.opcode) && inst.args.length == 2) {
                if (inst.args[0].equals(inst.args[1])) {
                    insts.remove(i);
                    i--;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean optimizeLiMove() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 1; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            if (i1.isEmpty() || i2.isEmpty())
                continue;

            if ("li".equals(i1.opcode) && "move".equals(i2.opcode)) {
                String tReg = i1.args[0];
                String imm = i1.args[1];
                String dst = i2.args[0];
                String src = i2.args[1];

                if (src.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        i2.opcode = "li";
                        i2.args = new String[] {dst, imm};
                        insts.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean optimizeMoveMove() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 1; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            if (i1.isEmpty() || i2.isEmpty())
                continue;

            if ("move".equals(i1.opcode) && "move".equals(i2.opcode)) {
                String tReg = i1.args[0];
                String src1 = i1.args[1];
                String dst2 = i2.args[0];
                String src2 = i2.args[1];

                if (src2.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        i2.args = new String[] {dst2, src1};
                        insts.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean optimizeMoveSw() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 1; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            if (i1.isEmpty() || i2.isEmpty())
                continue;

            if ("move".equals(i1.opcode) && "sw".equals(i2.opcode)) {
                String tReg = i1.args[0];
                String src1 = i1.args[1];
                String src2 = i2.args[0];
                String addr = i2.args[1];

                if (src2.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        i2.args = new String[] {src1, addr};
                        insts.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean optimizeMoveBranch() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 1; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            if (i1.isEmpty() || i2.isEmpty())
                continue;

            if ("move".equals(i1.opcode) && ("beq".equals(i2.opcode) || "bne".equals(i2.opcode))) {
                String tReg = i1.args[0];
                String src = i1.args[1];
                String op1 = i2.args[0];
                String op2 = i2.args[1];
                String label = i2.args[2];

                if (op1.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        i2.args = new String[] {src, op2, label};
                        insts.remove(i);
                        i--;
                        changed = true;
                    }
                } else if (op2.equals(tReg) && isTempReg(tReg)) {
                    if (isSafeToRemove(tReg, i + 2)) {
                        i2.args = new String[] {op1, src, label};
                        insts.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean optimizeMoveMemAddr() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 1; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            if (i1.isEmpty() || i2.isEmpty())
                continue;

            if ("move".equals(i1.opcode) && ("lw".equals(i2.opcode) || "sw".equals(i2.opcode))) {
                String tReg = i1.args[0];
                String baseSrc = i1.args[1];
                String memSrcDest = i2.args[0];
                String addr = i2.args[1];

                int parenIndex = addr.indexOf('(');
                int closeParenIndex = addr.indexOf(')');
                if (parenIndex != -1 && closeParenIndex != -1) {
                    String offset = addr.substring(0, parenIndex);
                    String baseReg = addr.substring(parenIndex + 1, closeParenIndex);

                    if (baseReg.equals(tReg) && isTempReg(tReg)) {
                        if (isSafeToRemove(tReg, i + 2)) {
                            i2.args = new String[] {memSrcDest, offset + "(" + baseSrc + ")"};
                            insts.remove(i);
                            i--;
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean removeRedundantJumps() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 1; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            if (i1.isEmpty() || i2.isEmpty())
                continue;

            if ("j".equals(i1.opcode) && i2.isLabel()) {
                if (i1.args[0].equals(i2.label)) {
                    insts.remove(i);
                    i--;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean optimizeBranchInversion() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 2; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            Inst i3 = insts.get(i + 2);
            if (i1.isEmpty() || i2.isEmpty() || i3.isEmpty())
                continue;

            if (("beq".equals(i1.opcode) || "bne".equals(i1.opcode)) && "j".equals(i2.opcode) && i3.isLabel()) {
                String target1 = i1.args[2];
                String target2 = i2.args[0];
                String label = i3.label;

                if (target1.equals(label)) {
                    i1.opcode = "beq".equals(i1.opcode) ? "bne" : "beq";
                    i1.args[2] = target2;
                    insts.remove(i + 1);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean optimizeLoadStoreForwarding() {
        boolean changed = false;
        for (int i = 0; i < insts.size() - 1; i++) {
            Inst i1 = insts.get(i);
            Inst i2 = insts.get(i + 1);
            if (i1.isEmpty() || i2.isEmpty())
                continue;

            if ("sw".equals(i1.opcode) && "lw".equals(i2.opcode)) {
                String srcReg = i1.args[0];
                String addr1 = i1.args[1];
                String dstReg = i2.args[0];
                String addr2 = i2.args[1];

                if (addr1.equals(addr2)) {
                    if (srcReg.equals(dstReg)) {
                        insts.remove(i + 1);
                    } else {
                        i2.opcode = "move";
                        i2.args = new String[] {dstReg, srcReg};
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean isSafeToRemove(String reg, int nextIndex) {
        for (int i = nextIndex; i < insts.size() && i < nextIndex + 20; i++) {
            Inst inst = insts.get(i);
            if (inst.isEmpty())
                continue;
            if (inst.isLabel())
                return false;
            if (inst.opcode.startsWith("j") || inst.opcode.startsWith("b"))
                return false;

            boolean usesReg = false;
            for (String arg : inst.args) {
                if (arg.contains(reg)) { // Simple check, might be too broad but safe
                    usesReg = true;
                    break;
                }
            }

            if (usesReg) {
                if (isDestOpcode(inst.opcode)) {
                    // If it's the destination, it's safe ONLY if it's not also a source operand
                    if (inst.args[0].equals(reg)) {
                        for (int j = 1; j < inst.args.length; j++) {
                            if (inst.args[j].contains(reg))
                                return false;
                        }
                        return true;
                    }
                    return false;
                }
                if (isSourceOpcode(inst.opcode)) {
                    return false;
                }
                // Default unsafe
                return false;
            }
        }
        return false;
    }

    private boolean isDestOpcode(String op) {
        return op.equals("add") || op.equals("addu") || op.equals("addiu") || op.equals("sub") || op.equals("subu")
            || op.equals("mul") || op.equals("sll") || op.equals("srl") || op.equals("sra") || op.equals("slt")
            || op.equals("slti") || op.equals("sltiu") || op.equals("sltu") || op.equals("and") || op.equals("or")
            || op.equals("xor") || op.equals("nor") || op.equals("andi") || op.equals("ori") || op.equals("xori")
            || op.equals("li") || op.equals("la") || op.equals("lw") || op.equals("lb") || op.equals("lh")
            || op.equals("move") || op.equals("mfhi") || op.equals("mflo");
    }

    private boolean isSourceOpcode(String op) {
        return op.equals("sw") || op.equals("sb") || op.equals("sh") || op.equals("beq") || op.equals("bne")
            || op.equals("bgt") || op.equals("bge") || op.equals("blt") || op.equals("ble") || op.equals("bgtz")
            || op.equals("blez") || op.equals("jr") || op.equals("jalr") || op.equals("mult") || op.equals("multu")
            || op.equals("div") || op.equals("divu");
    }

    private boolean isTempReg(String reg) {
        return reg.matches("\\$t[0-9]");
    }

    static class Inst {
        final String original;
        final String indent;
        String label;
        String opcode;
        String[] args;

        Inst(String line) {
            this.original = line;
            int i = 0;
            while (i < line.length() && Character.isWhitespace(line.charAt(i)))
                i++;
            indent = line.substring(0, i);

            String clean = line.trim();
            int commentIdx = clean.indexOf('#');
            if (commentIdx >= 0)
                clean = clean.substring(0, commentIdx).trim();

            if (clean.isEmpty())
                return;

            if (clean.contains(".asciiz") || clean.contains(".ascii")) {
                return;
            }

            if (clean.endsWith(":")) {
                label = clean.substring(0, clean.length() - 1);
                return;
            }

            int spaceIdx = clean.indexOf(' ');
            if (spaceIdx == -1) {
                opcode = clean;
                args = new String[0];
            } else {
                opcode = clean.substring(0, spaceIdx);
                String argsStr = clean.substring(spaceIdx + 1);
                args = argsStr.split("\\s*,\\s*");
            }
        }

        boolean isLabel() {
            return label != null;
        }

        boolean isEmpty() {
            return label == null && opcode == null;
        }

        @Override
        public String toString() {
            if (label != null)
                return label + ":";
            if (opcode == null)
                return original;
            return indent + opcode + " " + String.join(", ", args);
        }
    }
}