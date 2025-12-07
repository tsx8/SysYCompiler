package top.tsxb.compiler.ir.structure;

import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.constant.Constant;

public class GlobalVariable extends GlobalValue {
    private final boolean isConst;

    public GlobalVariable(String name, IrType valueType, boolean isConst, Constant initVal,
        GlobalValue.Linkage linkage) {
        super(valueType, name, linkage);
        this.isConst = isConst;
        if (initVal != null) {
            addOperand(initVal);
        }
    }

    public boolean isConst() {
        return isConst;
    }

    @Override
    public boolean isDeclaration() {
        return getNumOperands() == 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getRef()).append(" = ").append(linkage).append(" ");
        sb.append(isConst ? "constant " : "global ");
        sb.append(getValueType()).append(" ");
        if (isDeclaration()) {
            sb.append("zeroinitializer");
        } else {
            sb.append(getOperand(0).toString());
        }
        return sb.toString();
    }
}
