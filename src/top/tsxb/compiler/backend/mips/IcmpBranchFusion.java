package top.tsxb.compiler.backend.mips;

import java.util.List;

import top.tsxb.compiler.ir.base.Use;
import top.tsxb.compiler.ir.base.User;
import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.IcmpInst;

public final class IcmpBranchFusion {
    private IcmpBranchFusion() {
    }

    public static IcmpInst getFusableIcmpFromBr(BrInst br) {
        if (br == null || br.getNumOperands() != 3) {
            return null;
        }
        Value cond = br.getOperand(0);
        if (!(cond instanceof IcmpInst icmp)) {
            return null;
        }
        if (!isFusableIcmp(icmp)) {
            return null;
        }
        List<Use> uses = icmp.getUseList();
        if (uses.size() != 1) {
            return null;
        }
        User user = uses.get(0).user();
        if (user != br) {
            return null;
        }
        return icmp;
    }

    public static boolean isFusableIcmp(IcmpInst icmp) {
        if (icmp == null) {
            return false;
        }
        List<Use> uses = icmp.getUseList();
        if (uses.size() != 1) {
            return false;
        }
        User user = uses.get(0).user();
        if (!(user instanceof BrInst br)) {
            return false;
        }
        return br.getNumOperands() == 3 && br.getOperand(0) == icmp && br.getParent() == icmp.getParent();
    }
}
