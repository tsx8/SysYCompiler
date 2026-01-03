package top.tsxb.compiler.ir.inst;

import java.util.List;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.NoneType;

public class CallInst extends Instruction {
    public CallInst(Function func, List<Value> args, BasicBlock parent) {
        super(((FuncType)func.getValueType()).getReturnType(), OpCode.CALL, "call", parent);
        addOperand(func);
        for (Value arg : args) {
            addOperand(arg);
        }
    }

    @Override
    public String toString() {
        Function func = (Function)getOperand(0);
        StringBuilder sb = new StringBuilder();
        if (!(type instanceof NoneType)) {
            sb.append(getRef()).append(" = ");
        }
        sb.append("call ").append(type).append(" ").append(func.getRef()).append("(");
        List<String> args =
            operands.subList(1, operands.size()).stream().map(arg -> arg.getType() + " " + arg.getRef()).toList();
        sb.append(String.join(", ", args));
        sb.append(")");
        return sb.toString();
    }
}
