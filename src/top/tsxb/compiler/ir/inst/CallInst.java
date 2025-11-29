package top.tsxb.compiler.ir.inst;

import java.util.List;

import top.tsxb.compiler.ir.value.Value;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.Function;

public class CallInst extends Instruction {
    public CallInst(Function func, List<Value> args, BasicBlock parent) {
        super(((FuncType)func.getType()).getReturnType(), OpCode.CALL, "", parent);
        addOperand(func);
        for (Value arg : args) {
            addOperand(arg);
        }
    }

    @Override
    public String toString() {
        Function func = (Function)getOperand(0);
        StringBuilder sb = new StringBuilder();
        if (!type.isVoid()) {
            sb.append(name).append(" = ");
        }
        sb.append("call ").append(type).append(" ").append(func.getName()).append("(");
        for (int i = 1; i < getNumOperands(); i++) {
            Value arg = getOperand(i);
            sb.append(arg.getType().toString()).append(" ").append(arg.getName());
            if (i < getNumOperands() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
