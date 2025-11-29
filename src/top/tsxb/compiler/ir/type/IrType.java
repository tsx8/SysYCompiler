package top.tsxb.compiler.ir.type;

public abstract class IrType {
    @Override
    public abstract String toString();

    public boolean isVoid() {
        return false;
    }

    public boolean isInteger() {
        return false;
    }
}
