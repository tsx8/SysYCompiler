package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.structure.Argument;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalVariable;
import top.tsxb.compiler.ir.type.IntType;

import java.util.*;

public class SccpPass implements Pass {
    private enum LatticeStatus {
        TOP, CONSTANT, BOTTOM
    }

    private static class LatticeValue {
        LatticeStatus status;
        Integer value;

        LatticeValue(LatticeStatus status, Integer value) {
            this.status = status;
            this.value = value;
        }

        static LatticeValue top() { return new LatticeValue(LatticeStatus.TOP, null); }
        static LatticeValue bottom() { return new LatticeValue(LatticeStatus.BOTTOM, null); }
        static LatticeValue constant(int val) { return new LatticeValue(LatticeStatus.CONSTANT, val); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LatticeValue that = (LatticeValue) o;
            return status == that.status && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, value);
        }

        @Override
        public String toString() {
            return switch (status) {
                case TOP -> "TOP";
                case BOTTOM -> "BOTTOM";
                case CONSTANT -> "CONST(" + value + ")";
            };
        }
    }

    private final Map<Value, LatticeValue> latticeValues = new LinkedHashMap<>();
    private final Set<BasicBlock> reachableBlocks = new LinkedHashSet<>();
    private final Queue<BasicBlock> cfgWorklist = new LinkedList<>();
    private final Queue<Instruction> ssaWorklist = new LinkedList<>();
    private final Set<Edge> executableEdges = new LinkedHashSet<>();

    private record Edge(BasicBlock from, BasicBlock to) {}

    @Override
    public boolean run(top.tsxb.compiler.ir.structure.Module module) {
        boolean changed = false;
        for (Function function : module.getFunctionList()) {
            if (function.isDeclaration()) continue;
            changed |= runOnFunction(function);
        }
        return changed;
    }

    private boolean runOnFunction(Function function) {
        latticeValues.clear();
        reachableBlocks.clear();
        cfgWorklist.clear();
        ssaWorklist.clear();
        executableEdges.clear();

        // Initialize arguments to BOTTOM
        for (Argument arg : function.getArguments()) {
            latticeValues.put(arg, LatticeValue.bottom());
        }
        
        // Entry block is reachable
        if (!function.getBasicBlocks().isEmpty()) {
            cfgWorklist.add(function.getBasicBlocks().get(0));
        }
        
        while (!cfgWorklist.isEmpty() || !ssaWorklist.isEmpty()) {
            if (!cfgWorklist.isEmpty()) {
                BasicBlock bb = cfgWorklist.poll();
                if (reachableBlocks.contains(bb)) continue;
                reachableBlocks.add(bb);
                for (Instruction inst : bb.getInstructions()) {
                    visitInstruction(inst);
                }
            } else {
                Instruction inst = ssaWorklist.poll();
                // Only visit if the block is reachable
                if (reachableBlocks.contains(inst.getParent())) {
                    visitInstruction(inst);
                }
            }
        }

        return rewriteFunction(function);
    }

    private LatticeValue getLatticeValue(Value v) {
        if (v instanceof ConstInt ci) {
            return LatticeValue.constant(ci.getValue());
        }
        if (v instanceof GlobalVariable gv && gv.isConst()) {
            if (gv.getNumOperands() > 0 && gv.getOperand(0) instanceof ConstInt ci) {
                return LatticeValue.constant(ci.getValue());
            }
        }
        return latticeValues.getOrDefault(v, LatticeValue.top());
    }

    private void setLatticeValue(Value v, LatticeValue lv) {
        LatticeValue old = getLatticeValue(v);
        if (!old.equals(lv)) {
            latticeValues.put(v, lv);
            // Add all users to ssaWorklist
            for (var use : v.getUseList()) {
                if (use.user() instanceof Instruction inst) {
                    ssaWorklist.add(inst);
                }
            }
        }
    }

    private void visitInstruction(Instruction inst) {
        if (inst instanceof BinaryInst binary) {
            visitBinary(binary);
        } else if (inst instanceof IcmpInst icmp) {
            visitIcmp(icmp);
        } else if (inst instanceof PhiInst phi) {
            visitPhi(phi);
        } else if (inst instanceof BrInst br) {
            visitBr(br);
        } else if (inst instanceof ZextInst zext) {
            visitZext(zext);
        } else if (inst instanceof LoadInst load) {
            visitLoad(load);
        } else {
            // Default to BOTTOM for other instructions (Store, Call, Alloca, GEP, etc.)
            setLatticeValue(inst, LatticeValue.bottom());
        }
    }

    private void visitBinary(BinaryInst binary) {
        LatticeValue v1 = getLatticeValue(binary.getOperand(0));
        LatticeValue v2 = getLatticeValue(binary.getOperand(1));

        if (v1.status == LatticeStatus.CONSTANT && v2.status == LatticeStatus.CONSTANT) {
            ConstInt res = ConstantFolder.foldBinary(binary.getOpCode(), 
                new ConstInt(IntType.I32, v1.value), 
                new ConstInt(IntType.I32, v2.value));
            if (res != null) {
                setLatticeValue(binary, LatticeValue.constant(res.getValue()));
                return;
            }
        }
        
        // Special cases for MUL by 0
        if (binary.getOpCode() == OpCode.MUL) {
            if ((v1.status == LatticeStatus.CONSTANT && v1.value == 0) ||
                (v2.status == LatticeStatus.CONSTANT && v2.value == 0)) {
                setLatticeValue(binary, LatticeValue.constant(0));
                return;
            }
        }

        if (v1.status == LatticeStatus.BOTTOM || v2.status == LatticeStatus.BOTTOM) {
            setLatticeValue(binary, LatticeValue.bottom());
        } else {
            setLatticeValue(binary, LatticeValue.top());
        }
    }

    private void visitIcmp(IcmpInst icmp) {
        LatticeValue v1 = getLatticeValue(icmp.getOperand(0));
        LatticeValue v2 = getLatticeValue(icmp.getOperand(1));

        if (v1.status == LatticeStatus.CONSTANT && v2.status == LatticeStatus.CONSTANT) {
            ConstInt res = ConstantFolder.foldIcmp(icmp.getPredicate(),
                new ConstInt(IntType.I32, v1.value),
                new ConstInt(IntType.I32, v2.value));
            if (res != null) {
                setLatticeValue(icmp, LatticeValue.constant(res.getValue()));
                return;
            }
        }

        if (v1.status == LatticeStatus.BOTTOM || v2.status == LatticeStatus.BOTTOM) {
            setLatticeValue(icmp, LatticeValue.bottom());
        } else {
            setLatticeValue(icmp, LatticeValue.top());
        }
    }

    private void visitZext(ZextInst zext) {
        LatticeValue v = getLatticeValue(zext.getOperand(0));
        if (v.status == LatticeStatus.BOTTOM) {
            setLatticeValue(zext, LatticeValue.bottom());
        } else if (v.status == LatticeStatus.CONSTANT) {
            setLatticeValue(zext, LatticeValue.constant(v.value));
        } else {
            setLatticeValue(zext, LatticeValue.top());
        }
    }

    private void visitLoad(LoadInst load) {
        Value ptr = load.getOperand(0);
        if (ptr instanceof GlobalVariable gv && gv.isConst()) {
            if (gv.getNumOperands() > 0 && gv.getOperand(0) instanceof ConstInt ci) {
                setLatticeValue(load, LatticeValue.constant(ci.getValue()));
                return;
            }
        }
        setLatticeValue(load, LatticeValue.bottom());
    }

    private void visitPhi(PhiInst phi) {
        LatticeValue res = LatticeValue.top();
        boolean hasExecutableEdge = false;
        for (Map.Entry<BasicBlock, Value> entry : phi.getIncoming().entrySet()) {
            if (executableEdges.contains(new Edge(entry.getKey(), phi.getParent()))) {
                res = meet(res, getLatticeValue(entry.getValue()));
                hasExecutableEdge = true;
            }
        }
        if (!hasExecutableEdge) {
            setLatticeValue(phi, LatticeValue.top());
        } else {
            setLatticeValue(phi, res);
        }
    }

    private LatticeValue meet(LatticeValue v1, LatticeValue v2) {
        if (v1.status == LatticeStatus.BOTTOM || v2.status == LatticeStatus.BOTTOM) return LatticeValue.bottom();
        if (v1.status == LatticeStatus.TOP) return v2;
        if (v2.status == LatticeStatus.TOP) return v1;
        if (v1.value.equals(v2.value)) return v1;
        return LatticeValue.bottom();
    }

    private void visitBr(BrInst br) {
        if (br.getNumOperands() == 1) {
            BasicBlock dest = (BasicBlock) br.getOperand(0);
            addEdge(br.getParent(), dest);
        } else {
            LatticeValue cond = getLatticeValue(br.getOperand(0));
            if (cond.status == LatticeStatus.CONSTANT) {
                if (cond.value != 0) {
                    addEdge(br.getParent(), (BasicBlock) br.getOperand(1));
                } else {
                    addEdge(br.getParent(), (BasicBlock) br.getOperand(2));
                }
            } else if (cond.status == LatticeStatus.BOTTOM) {
                addEdge(br.getParent(), (BasicBlock) br.getOperand(1));
                addEdge(br.getParent(), (BasicBlock) br.getOperand(2));
            }
        }
    }

    private void addEdge(BasicBlock from, BasicBlock to) {
        Edge edge = new Edge(from, to);
        if (!executableEdges.contains(edge)) {
            executableEdges.add(edge);
            if (reachableBlocks.contains(to)) {
                // If block was already reachable, we might need to update PHIs
                for (Instruction inst : to.getInstructions()) {
                    if (inst instanceof PhiInst) {
                        ssaWorklist.add(inst);
                    }
                }
            } else {
                cfgWorklist.add(to);
            }
        }
    }

    private boolean rewriteFunction(Function function) {
        boolean changed = false;
        
        // 1. Replace constant instructions with ConstInt
        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = new ArrayList<>(bb.getInstructions());
            for (Instruction inst : insts) {
                LatticeValue lv = getLatticeValue(inst);
                if (lv.status == LatticeStatus.CONSTANT) {
                    inst.replaceAllUsesWith(new ConstInt((IntType) inst.getType(), lv.value));
                    inst.dropAllReferences();
                    bb.getInstructions().remove(inst);
                    changed = true;
                }
            }
        }

        // 2. Simplify branches
        for (BasicBlock bb : function.getBasicBlocks()) {
            if (bb.getInstructions().isEmpty()) continue;
            Instruction last = bb.getInstructions().get(bb.getInstructions().size() - 1);
            if (last instanceof BrInst br && br.getNumOperands() == 3) {
                LatticeValue cond = getLatticeValue(br.getOperand(0));
                if (cond.status == LatticeStatus.CONSTANT) {
                    BasicBlock dest = (BasicBlock) (cond.value != 0 ? br.getOperand(1) : br.getOperand(2));
                    
                    // Replace with unconditional branch
                    br.dropAllReferences();
                    bb.getInstructions().remove(br);
                    new BrInst(dest, bb);
                    changed = true;
                }
            }
        }

        // 3. Remove unreachable blocks
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
        for (BasicBlock bb : blocks) {
            if (!reachableBlocks.contains(bb)) {
                for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                    inst.dropAllReferences();
                }
                function.removeBasicBlock(bb);
                changed = true;
            }
        }
        
        // 4. Clean up PHIs in reachable blocks
        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = new ArrayList<>(bb.getInstructions());
            for (Instruction inst : insts) {
                if (inst instanceof PhiInst phi) {
                    Map<BasicBlock, Value> incoming = phi.getIncoming();
                    for (BasicBlock pred : incoming.keySet()) {
                        if (!executableEdges.contains(new Edge(pred, bb))) {
                            phi.removeIncoming(pred);
                            changed = true;
                        }
                    }
                    // If PHI has only one incoming value, replace it
                    if (phi.getNumOperands() == 1) {
                        phi.replaceAllUsesWith(phi.getOperand(0));
                        phi.dropAllReferences();
                        bb.getInstructions().remove(phi);
                        changed = true;
                    } else if (phi.getNumOperands() == 0) {
                        phi.dropAllReferences();
                        bb.getInstructions().remove(phi);
                        changed = true;
                    }
                }
            }
        }

        changed |= applyAlgebraicSimplifications(function);

        return changed;
    }

    private boolean applyAlgebraicSimplifications(Function function) {
        boolean changed = false;
        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> insts = new ArrayList<>(bb.getInstructions());
            for (Instruction inst : insts) {
                if (inst instanceof BinaryInst binary) {
                    Value simplified = simplifyBinary(binary);
                    if (simplified != null) {
                        binary.replaceAllUsesWith(simplified);
                        binary.dropAllReferences();
                        bb.getInstructions().remove(binary);
                        changed = true;
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
                        }
                    }
                }
            }
        }
        return changed;
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
