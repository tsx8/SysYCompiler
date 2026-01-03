package top.tsxb.compiler.backend.opti;

import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.OpCode;
import top.tsxb.compiler.ir.type.IntType;

public class ConstantFolder {
    public static ConstInt foldBinary(OpCode op, ConstInt left, ConstInt right) {
        int l = left.getValue();
        int r = right.getValue();
        int res;
        switch (op) {
            case ADD -> res = l + r;
            case SUB -> res = l - r;
            case MUL -> res = l * r;
            case SDIV -> {
                if (r == 0)
                    return null;
                res = l / r;
            }
            case SREM -> {
                if (r == 0)
                    return null;
                res = l % r;
            }
            default -> {
                return null;
            }
        }
        return new ConstInt(IntType.I32, res);
    }

    public static ConstInt foldIcmp(IcmpInst.CondCode cond, ConstInt left, ConstInt right) {
        int l = left.getValue();
        int r = right.getValue();
        boolean res;
        switch (cond) {
            case EQ -> res = l == r;
            case NE -> res = l != r;
            case SGT -> res = l > r;
            case SGE -> res = l >= r;
            case SLT -> res = l < r;
            case SLE -> res = l <= r;
            default -> {
                return null;
            }
        }
        return new ConstInt(IntType.I1, res ? 1 : 0);
    }
}
