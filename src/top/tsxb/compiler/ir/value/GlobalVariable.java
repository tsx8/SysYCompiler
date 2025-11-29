package top.tsxb.compiler.ir.value;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.PtrType;

public class GlobalVariable extends User {
    public enum Linkage {
        EXTERNAL("dso_local"),
        INTERNAL("internal");

        private final String name;

        Linkage(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    private final boolean isConst;
    private final Constant initVal;
    private final Linkage linkage;

    public GlobalVariable(String name, IrType type, boolean isConst, Constant initVal, Linkage linkage) {
        super(new PtrType(type), "@" + name);
        this.isConst = isConst;
        this.initVal = initVal;
        this.linkage = linkage;
        if (initVal != null) {
            addOperand(initVal);
        }
    }

    public GlobalVariable(String name, IrType type, boolean isConst, Constant initVal) {
        this(name, type, isConst, initVal, Linkage.EXTERNAL);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" = ").append(linkage.toString()).append(" ");
        sb.append(isConst ? "constant " : "global ");
        IrType innerType = ((PtrType) this.type).getPointeeType();
        sb.append(innerType.toString()).append(" ");

        if (initVal != null) {
            sb.append(initVal);
        } else {
            sb.append("zeroinitialzer");
        }

        return sb.toString();
    }
}
