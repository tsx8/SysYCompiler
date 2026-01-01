package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.IntType;

import java.util.ArrayList;
import java.util.List;

public class ConstantFoldingPass implements Pass {
    @Override
    public boolean run(Module module) {
        boolean anyChanged = false;
        for (Function function : module.getFunctionList()) {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (BasicBlock bb : function.getBasicBlocks()) {
                    List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
                    for (Instruction inst : instructions) {
                        if (inst instanceof BinaryInst binary) {
                            Value left = binary.getOperand(0);
                            Value right = binary.getOperand(1);
                            if (left instanceof ConstInt l && right instanceof ConstInt r) {
                                ConstInt res = ConstantFolder.foldBinary(binary.getOpCode(), l, r);
                                if (res != null) {
                                    binary.replaceAllUsesWith(res);
                                    binary.dropAllReferences();
                                    bb.getInstructions().remove(binary);
                                    changed = true;
                                    anyChanged = true;
                                }
                            } else {
                                Value simplified = simplifyBinary(binary);
                                if (simplified != null) {
                                    binary.replaceAllUsesWith(simplified);
                                    binary.dropAllReferences();
                                    bb.getInstructions().remove(binary);
                                    changed = true;
                                    anyChanged = true;
                                } else {
                                    // Strength reduction: mul x, -1 -> sub 0, x; sdiv x, -1 -> sub 0, x
                                    Value x = null;
                                    if (binary.getOpCode() == OpCode.MUL) {
                                        if (isConstant(binary.getOperand(0), -1)) x = binary.getOperand(1);
                                        else if (isConstant(binary.getOperand(1), -1)) x = binary.getOperand(0);
                                    } else if (binary.getOpCode() == OpCode.SDIV) {
                                        if (isConstant(binary.getOperand(1), -1)) x = binary.getOperand(0);
                                    }

                                    if (x != null) {
                                        BinaryInst sub = new BinaryInst(OpCode.SUB, new ConstInt(IntType.I32, 0), x, null);
                                        int index = bb.getInstructions().indexOf(binary);
                                        bb.getInstructions().set(index, sub);
                                        sub.setParent(bb);
                                        if (bb.getParent() != null) {
                                            bb.getParent().resolveLocalName(sub);
                                        }
                                        binary.replaceAllUsesWith(sub);
                                        binary.dropAllReferences();
                                        changed = true;
                                        anyChanged = true;
                                    }
                                }
                            }
                        } else if (inst instanceof IcmpInst icmp) {
                            Value left = icmp.getOperand(0);
                            Value right = icmp.getOperand(1);
                            if (left instanceof ConstInt l && right instanceof ConstInt r) {
                                ConstInt res = ConstantFolder.foldIcmp(icmp.getPredicate(), l, r);
                                if (res != null) {
                                    icmp.replaceAllUsesWith(res);
                                    icmp.dropAllReferences();
                                    bb.getInstructions().remove(icmp);
                                    changed = true;
                                    anyChanged = true;
                                }
                            }
                        } else if (inst instanceof PhiInst phi) {
                            Value first = null;
                            boolean allSame = true;
                            for (int i = 0; i < phi.getNumOperands(); i++) {
                                Value v = phi.getOperand(i);
                                if (v == phi) continue;
                                if (first == null) {
                                    first = v;
                                } else if (v != first) {
                                    allSame = false;
                                    break;
                                }
                            }
                            if (allSame && first != null) {
                                phi.replaceAllUsesWith(first);
                                phi.dropAllReferences();
                                bb.getInstructions().remove(phi);
                                changed = true;
                                anyChanged = true;
                            }
                        } else if (inst instanceof BrInst br && br.getNumOperands() == 3) {
                            Value cond = br.getOperand(0);
                            if (cond instanceof ConstInt c) {
                                BasicBlock target = c.getValue() != 0 ? (BasicBlock) br.getOperand(1) : (BasicBlock) br.getOperand(2);
                                BrInst newBr = new BrInst(target, null);
                                int index = bb.getInstructions().indexOf(br);
                                bb.getInstructions().set(index, newBr);
                                newBr.setParent(bb);
                                br.dropAllReferences();
                                changed = true;
                                anyChanged = true;
                            }
                        } else if (inst instanceof ZextInst zext) {
                            Value val = zext.getOperand(0);
                            if (val instanceof ConstInt c) {
                                ConstInt res = new ConstInt((IntType) zext.getType(), c.getValue());
                                zext.replaceAllUsesWith(res);
                                zext.dropAllReferences();
                                bb.getInstructions().remove(zext);
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
        return anyChanged;
    }

    private Value simplifyBinary(BinaryInst binary) {
        OpCode op = binary.getOpCode();
        Value left = binary.getOperand(0);
        Value right = binary.getOperand(1);

        if (op == OpCode.ADD) {
            if (isConstant(left, 0)) return right;
            if (isConstant(right, 0)) return left;
        } else if (op == OpCode.SUB) {
            if (isConstant(right, 0)) return left;
            if (left == right) return new ConstInt(IntType.I32, 0);
            // 0 - (0 - x) -> x
            if (isConstant(left, 0) && right instanceof BinaryInst rBin && rBin.getOpCode() == OpCode.SUB && isConstant(rBin.getOperand(0), 0)) {
                return rBin.getOperand(1);
            }
        } else if (op == OpCode.MUL) {
            if (isConstant(left, 1)) return right;
            if (isConstant(right, 1)) return left;
            if (isConstant(left, 0)) return left;
            if (isConstant(right, 0)) return right;
        } else if (op == OpCode.SDIV) {
            if (isConstant(right, 1)) return left;
            if (isConstant(left, 0)) return left;
            if (left == right) return new ConstInt(IntType.I32, 1);
        } else if (op == OpCode.SREM) {
            if (isConstant(right, 1)) return new ConstInt(IntType.I32, 0);
            if (isConstant(left, 0)) return left;
            if (left == right) return new ConstInt(IntType.I32, 0);
        }
        return null;
    }

    private boolean isConstant(Value v, int val) {
        return v instanceof ConstInt c && c.getValue() == val;
    }
}
