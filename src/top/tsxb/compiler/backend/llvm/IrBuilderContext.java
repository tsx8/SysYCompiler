package top.tsxb.compiler.backend.llvm;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import top.tsxb.compiler.frontend.semantic.sym.Symbol;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.OpCode;
import top.tsxb.compiler.ir.inst.ZextInst;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.type.PtrType;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.ConstInt;
import top.tsxb.compiler.ir.value.Function;
import top.tsxb.compiler.ir.value.Value;

public class IrBuilderContext {
    private final Map<Symbol, Value> symValMap = new LinkedHashMap<>();
    private final Map<String, Function> builtins = new LinkedHashMap<>();
    private final Stack<LoopInfo> loopStack = new Stack<>();

    public Function currentFunction;
    public BasicBlock currentBlock;

    public IrBuilderContext() {
        addBuiltin("getint", IntType.I32, List.of());
        addBuiltin("putint", NoneType.VOID, List.of(IntType.I32));
        addBuiltin("putch", NoneType.VOID, List.of(IntType.I32));
        addBuiltin("putstr", NoneType.VOID, List.of(new PtrType(IntType.I8)));
    }

    public Value ensureI1(Value val) {
        if (val.getType() instanceof IntType it && it.getBitWidth() == 32) {
            return new IcmpInst(IcmpInst.CondCode.NE, val, ConstInt.ZERO, currentBlock);
        }
        return val;
    }

    public Value ensureI32(Value val) {
        if (val.getType() instanceof IntType it && it.getBitWidth() == 1) {
            return new ZextInst(val, IntType.I32, currentBlock);
        }
        return val;
    }

    public Collection<Function> getBuiltins() {
        return builtins.values();
    }

    public void registerSymbol(Symbol symbol, Value value) {
        symValMap.put(symbol, value);
    }

    public Optional<Value> lookupSymbol(Symbol symbol) {
        var res = symValMap.get(symbol);
        if (res != null) {
            return Optional.of(res);
        }
        res = builtins.get(symbol.name());
        return Optional.ofNullable(res);
    }

    public Optional<Function> lookupBuiltin(String name) {
        return Optional.ofNullable(builtins.get(name));
    }

    public void enterLoop(BasicBlock step, BasicBlock exit) {
        loopStack.push(new LoopInfo(step, exit));
    }

    public void exitLoop() {
        if (!loopStack.isEmpty()) {
            loopStack.pop();
        }
    }

    public Optional<LoopInfo> currentLoop() {
        return loopStack.isEmpty() ? Optional.empty() : Optional.of(loopStack.peek());
    }

    public void jump(BasicBlock target) {
        if (currentBlock != null && !blockTerminated()) {
            new BrInst(target, currentBlock);
        }
    }

    public void condJump(Value cond, BasicBlock trueBlock, BasicBlock falseBlock) {
        if (currentBlock != null && !blockTerminated()) {
            new BrInst(ensureI1(cond), trueBlock, falseBlock, currentBlock);
        }
    }

    public boolean blockTerminated() {
        var insts = currentBlock.getInstructions();
        if (insts.isEmpty()) {
            return false;
        }
        Instruction last = insts.get(insts.size() - 1);
        return last.getOpCode() == OpCode.RET || last.getOpCode() == OpCode.BR;
    }

    private void addBuiltin(String name, IrType retType, List<IrType> paramTypes) {
        Function func = new Function(name, new FuncType(retType, paramTypes), true);
        builtins.put(name, func);
    }

    public record LoopInfo(BasicBlock step, BasicBlock exit) {
    }
}
