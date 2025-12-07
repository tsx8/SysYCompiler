package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.base.Value;

public class IcmpInst extends Instruction {
    private final CondCode predicate;

    public IcmpInst(CondCode predicate, Value lhs, Value rhs, BasicBlock parent) {
        super(IntType.I1, OpCode.ICMP, "icmp", parent);
        this.predicate = predicate;
        addOperand(lhs);
        addOperand(rhs);
    }

    @Override
    public String toString() {
        Value lhs = getOperand(0);
        Value rhs = getOperand(1);
        return String.format("%s = icmp %s %s %s, %s", getRef(), predicate, lhs.getType(), lhs.getRef(), rhs.getRef());
    }

    public enum CondCode {
        EQ("eq"), NE("ne"), SGT("sgt"), SGE("sge"), SLT("slt"), SLE("sle");

        private final String text;

        CondCode(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
