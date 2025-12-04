package top.tsxb.compiler.ir.inst;

import java.util.List;

import top.tsxb.compiler.ir.type.ArrType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.PtrType;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.Value;

public class GetElementPtrInst extends Instruction {
    private final IrType sourceElementType;

    public GetElementPtrInst(Value basePtr, List<Value> indices, BasicBlock parent) {
        super(computeResultType(basePtr, indices), OpCode.GEP, "", parent);
        sourceElementType = ((PtrType)basePtr.getType()).getPointeeType();
        addOperand(basePtr);
        for (Value index : indices) {
            addOperand(index);
        }
    }

    private static IrType computeResultType(Value basePtr, List<Value> indices) {
        IrType currentType = ((PtrType)basePtr.getType()).getPointeeType();
        for (int i = 1; i < indices.size(); i++) {
            if (currentType instanceof ArrType at) {
                currentType = at.getElementType();
            }
        }
        return new PtrType(currentType);
    }

    @Override
    public String toString() {
        Value base = getOperand(0);
        StringBuilder sb = new StringBuilder();
        sb.append(getRef()).append(" = getelementptr ");
        sb.append(sourceElementType).append(", ");
        sb.append(base.getType()).append(" ").append(base.getRef());

        for (int i = 1; i < getNumOperands(); i++) {
            sb.append(", ");
            Value idx = getOperand(i);
            sb.append(idx.getType()).append(" ").append(idx.getRef());
        }

        return sb.toString();
    }
}
