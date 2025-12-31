package top.tsxb.compiler.ir.structure;

import top.tsxb.compiler.ir.constant.Constant;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.PtrType;

public abstract class GlobalValue extends Constant {
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

    protected final Linkage linkage;

    public GlobalValue(IrType type, String name, Linkage linkage) {
        super(new PtrType(type), name);
        this.linkage = linkage;
    }

    public IrType getValueType() {
        return ((PtrType) this.type).getPointeeType();
    }

    public abstract boolean isDeclaration();

    @Override
    public String getRef() {
        return "@" + name;
    }
}
