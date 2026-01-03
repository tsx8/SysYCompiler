package top.tsxb.compiler.backend.llvm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import top.tsxb.compiler.frontend.semantic.sym.Symbol;
import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstInt;
import top.tsxb.compiler.ir.inst.BrInst;
import top.tsxb.compiler.ir.inst.IcmpInst;
import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.inst.OpCode;
import top.tsxb.compiler.ir.inst.ZextInst;
import top.tsxb.compiler.ir.structure.BasicBlock;
import top.tsxb.compiler.ir.structure.Function;
import top.tsxb.compiler.ir.structure.GlobalValue;
import top.tsxb.compiler.ir.structure.Module;
import top.tsxb.compiler.ir.type.IntType;

public class IrBuilderContext {
    private final Module module;
    private final Map<Symbol, Value> symValMap = new LinkedHashMap<>();
    private final Stack<LoopInfo> loopStack = new Stack<>();

    public Function currentFunction;
    public BasicBlock currentBlock;

    public IrBuilderContext(Module module) {
        this.module = module;
    }

    public Value ensureI1(Value val) {
        if (val.getType() instanceof IntType it && it.getBitWidth() == 32) {
            return new IcmpInst(IcmpInst.CondCode.NE, val, new ConstInt(IntType.I32, 0), currentBlock);
        }
        return val;
    }

    public Value ensureI32(Value val) {
        if (val == null)
            return new ConstInt(IntType.I32, 0);
        if (val.getType() instanceof IntType it && it.getBitWidth() == 1) {
            return new ZextInst(val, IntType.I32, currentBlock);
        }
        return val;
    }

    public void registerSymbol(Symbol symbol, Value value) {
        symValMap.put(symbol, value);
    }

    public Optional<Value> lookupSymbol(Symbol symbol) {
        var res = symValMap.get(symbol);
        if (res != null) {
            return Optional.of(res);
        }
        GlobalValue gv = module.getNamedGlobal(symbol.name());
        return Optional.ofNullable(gv);
    }

    public Optional<Function> lookupBuiltin(String name) {
        GlobalValue global = module.getNamedGlobal(name);
        if (global instanceof Function func) {
            return Optional.of(func);
        }
        return Optional.empty();
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

    public record LoopInfo(BasicBlock step, BasicBlock exit) {
    }
}
