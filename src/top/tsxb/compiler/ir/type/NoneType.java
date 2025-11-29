package top.tsxb.compiler.ir.type;

public class NoneType extends IrType {
    public static final NoneType VOID = new NoneType();

    private NoneType() {}

    @Override
    public String toString() {
        return "void";
    }

    @Override
    public boolean isVoid() {
        return true;
    }
}
