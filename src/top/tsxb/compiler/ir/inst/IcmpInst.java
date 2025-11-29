package top.tsxb.compiler.ir.inst;

import top.tsxb.compiler.ir.value.Value;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.value.BasicBlock;

public class IcmpInst extends Instruction {
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

    private final CondCode predicate;

    public IcmpInst(CondCode predicate, Value lhs, Value rhs, BasicBlock parent) {
        super(IntType.I1, OpCode.ICMP, "", parent);
        this.predicate = predicate;
        addOperand(lhs);
        addOperand(rhs);
    }

    @Override
    public String toString() {
        Value lhs = getOperand(0);
        Value rhs = getOperand(1);
        return String.format("%s = icmp %s %s %s, %s", name, predicate.toString(), lhs.getType().toString(),
            lhs.getName(), rhs.getName());
    }
}
