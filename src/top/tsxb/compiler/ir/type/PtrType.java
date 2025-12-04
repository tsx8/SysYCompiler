package top.tsxb.compiler.ir.type;

public class PtrType extends IrType {
    private final IrType pointeeType;

    public PtrType(IrType pointeeType) {
        this.pointeeType = pointeeType;
    }

    public IrType getPointeeType() {
        return pointeeType;
    }

    @Override
    public String toString() {
        return pointeeType + "*";
    }
}
