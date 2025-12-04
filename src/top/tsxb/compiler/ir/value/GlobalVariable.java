package top.tsxb.compiler.ir.value;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.PtrType;

public class GlobalVariable extends User {
    private final boolean isConst;
    private final Constant initVal;
    private final Linkage linkage;
    public GlobalVariable(String name, IrType type, boolean isConst, Constant initVal, Linkage linkage) {
        super(new PtrType(type), name);
        this.isConst = isConst;
        this.initVal = initVal;
        this.linkage = linkage;
    }

    public GlobalVariable(String name, IrType type, boolean isConst, Constant initVal) {
        this(name, type, isConst, initVal, Linkage.EXTERNAL);
    }

    @Override
    public String getRef() {
        return "@" + getName();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getRef()).append(" = ").append(linkage).append(" ");
        sb.append(isConst ? "constant " : "global ");
        IrType innerType = ((PtrType)this.type).getPointeeType();
        sb.append(innerType).append(" ");
        sb.append(initVal);
        return sb.toString();
    }

    public enum Linkage {
        EXTERNAL("dso_local"), INTERNAL("internal");

        private final String name;

        Linkage(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
