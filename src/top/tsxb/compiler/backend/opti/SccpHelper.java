package top.tsxb.compiler.backend.opti;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.BinaryInst;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.OpCode;
import top.tsxb.compiler.ir.inst.ZextInst;
import top.tsxb.compiler.ir.type.IntType;

public class SccpHelper {
    public static void visitBinary(BinaryInst binary, Function<Value, LatticeValue> getLatticeValue,
        Consumer<LatticeValue> setLatticeValue) {
        LatticeValue v1 = getLatticeValue.apply(binary.getOperand(0));
        LatticeValue v2 = getLatticeValue.apply(binary.getOperand(1));

        if (v1.status == LatticeStatus.CONSTANT && v2.status == LatticeStatus.CONSTANT) {
            ConstInt res = ConstantFolder.foldBinary(binary.getOpCode(), new ConstInt(IntType.I32, v1.value),
                new ConstInt(IntType.I32, v2.value));
            if (res != null) {
                setLatticeValue.accept(LatticeValue.constant(res.getValue()));
                return;
            }
        }

        if (binary.getOpCode() == OpCode.MUL) {
            if ((v1.status == LatticeStatus.CONSTANT && v1.value == 0)
                || (v2.status == LatticeStatus.CONSTANT && v2.value == 0)) {
                setLatticeValue.accept(LatticeValue.constant(0));
                return;
            }
        }

        if (v1.status == LatticeStatus.BOTTOM || v2.status == LatticeStatus.BOTTOM) {
            setLatticeValue.accept(LatticeValue.bottom());
        } else {
            setLatticeValue.accept(LatticeValue.top());
        }
    }

    public static void visitIcmp(IcmpInst icmp, Function<Value, LatticeValue> getLatticeValue,
        Consumer<LatticeValue> setLatticeValue) {
        LatticeValue v1 = getLatticeValue.apply(icmp.getOperand(0));
        LatticeValue v2 = getLatticeValue.apply(icmp.getOperand(1));

        if (v1.status == LatticeStatus.CONSTANT && v2.status == LatticeStatus.CONSTANT) {
            ConstInt res = ConstantFolder.foldIcmp(icmp.getPredicate(), new ConstInt(IntType.I32, v1.value),
                new ConstInt(IntType.I32, v2.value));
            if (res != null) {
                setLatticeValue.accept(LatticeValue.constant(res.getValue()));
                return;
            }
        }

        if (v1.status == LatticeStatus.BOTTOM || v2.status == LatticeStatus.BOTTOM) {
            setLatticeValue.accept(LatticeValue.bottom());
        } else {
            setLatticeValue.accept(LatticeValue.top());
        }
    }

    public static void visitZext(ZextInst zext, Function<Value, LatticeValue> getLatticeValue,
        Consumer<LatticeValue> setLatticeValue) {
        LatticeValue v = getLatticeValue.apply(zext.getOperand(0));
        if (v.status == LatticeStatus.CONSTANT) {
            setLatticeValue.accept(LatticeValue.constant(v.value));
        } else if (v.status == LatticeStatus.BOTTOM) {
            setLatticeValue.accept(LatticeValue.bottom());
        } else {
            setLatticeValue.accept(LatticeValue.top());
        }
    }

    public enum LatticeStatus {
        TOP, CONSTANT, BOTTOM
    }

    public record LatticeValue(LatticeStatus status, Integer value) {
        public static LatticeValue top() {
            return new LatticeValue(LatticeStatus.TOP, null);
        }

        public static LatticeValue bottom() {
            return new LatticeValue(LatticeStatus.BOTTOM, null);
        }

        public static LatticeValue constant(int val) {
            return new LatticeValue(LatticeStatus.CONSTANT, val);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            LatticeValue that = (LatticeValue)o;
            return status == that.status && Objects.equals(value, that.value);
        }

        @Override
        public String toString() {
            return switch (status) {
                case TOP -> "TOP";
                case BOTTOM -> "BOTTOM";
                case CONSTANT -> "CONST(" + value + ")";
            };
        }

        public LatticeValue meet(LatticeValue other) {
            if (this.status == LatticeStatus.BOTTOM || other.status == LatticeStatus.BOTTOM) {
                return bottom();
            }
            if (this.status == LatticeStatus.TOP) {
                return other;
            }
            if (other.status == LatticeStatus.TOP) {
                return this;
            }
            if (this.status == LatticeStatus.CONSTANT && other.status == LatticeStatus.CONSTANT) {
                if (this.value.equals(other.value)) {
                    return this;
                }
            }
            return bottom();
        }
    }
}
