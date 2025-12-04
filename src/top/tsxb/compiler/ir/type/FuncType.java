package top.tsxb.compiler.ir.type;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FuncType extends IrType {
    private final IrType returnType;
    private final List<IrType> paramTypes;

    public FuncType(IrType returnType, List<IrType> paramTypes) {
        this.returnType = returnType;
        this.paramTypes = new ArrayList<>(paramTypes);
    }

    public IrType getReturnType() {
        return returnType;
    }

    public List<IrType> getParamTypes() {
        return paramTypes;
    }

    @Override
    public String toString() {
        String params = paramTypes.stream().map(IrType::toString).collect(Collectors.joining(", "));
        return returnType + "(" + params + ")";
    }
}
