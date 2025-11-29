package top.tsxb.compiler.backend.llvm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import top.tsxb.compiler.frontend.parser.cst.TokenType;
import top.tsxb.compiler.frontend.semantic.ast.*;
import top.tsxb.compiler.frontend.semantic.type.ArrayType;
import top.tsxb.compiler.frontend.semantic.type.FunctionType;
import top.tsxb.compiler.frontend.semantic.type.IntegerType;
import top.tsxb.compiler.frontend.semantic.type.Type;
import top.tsxb.compiler.frontend.semantic.type.VoidType;
import top.tsxb.compiler.ir.Module;
import top.tsxb.compiler.ir.inst.*;
import top.tsxb.compiler.ir.type.ArrType;
import top.tsxb.compiler.ir.type.FuncType;
import top.tsxb.compiler.ir.type.IntType;
import top.tsxb.compiler.ir.type.IrType;
import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.type.PtrType;
import top.tsxb.compiler.ir.value.Argument;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.ConstArray;
import top.tsxb.compiler.ir.value.ConstInt;
import top.tsxb.compiler.ir.value.ConstZero;
import top.tsxb.compiler.ir.value.Constant;
import top.tsxb.compiler.ir.value.Function;
import top.tsxb.compiler.ir.value.GlobalVariable;
import top.tsxb.compiler.ir.value.Value;

public class IrBuilder implements AstVisitor<Value> {
    private final Module module;
    private final IrBuilderContext context;

    public IrBuilder(Module module) {
        this.module = module;
        this.context = new IrBuilderContext();
        this.context.getBuiltins().forEach(this.module::addFunction);
    }

    @Override
    public Value visit(CompUnit node) {
        node.decls.forEach(decl -> decl.accept(this));
        return null;
    }

    @Override
    public Value visit(VarDecl node) {
        IrType irType = translate(node.symbol.type());
        if (node.symbol.scopeLevel() == 1 || node.symbol.isStatic()) {
            Constant initVal;
            var initialValues = node.symbol.initialValues();
            if (irType instanceof IntType it) {
                int val = !initialValues.isEmpty() ? initialValues.get(0) : 0;
                initVal = new ConstInt(it, val);
            } else if (irType instanceof ArrType at) {
                List<Constant> elements = new ArrayList<>();
                int totSize = at.getNumElements();
                for (int i = 0; i < totSize; i++) {
                    int val = i < initialValues.size() ? initialValues.get(i) : 0;
                    elements.add(new ConstInt((IntType)at.getElementType(), val));
                }
                initVal = new ConstArray(at, elements);
            } else {
                initVal = new ConstZero(irType);
            }
            GlobalVariable gv;
            if (node.symbol.isStatic()) {
                gv = new GlobalVariable(context.currentFunction.getRawName() + "." + node.name, irType,
                    node.symbol.isConst(), initVal, GlobalVariable.Linkage.INTERNAL);
            } else {
                gv = new GlobalVariable(node.name, irType, node.symbol.isConst(), initVal);
            }
            module.addGlobalVariable(gv);
            context.registerSymbol(node.symbol, gv);
            return null;
        }
        if (node.symbol.isConst()) {
            if (!node.symbol.initialValues().isEmpty()) {
                int constVal = node.symbol.initialValues().get(0);
                context.registerSymbol(node.symbol, new ConstInt(IntType.I32, constVal));
            }
            return null;
        }
        BasicBlock entry = context.currentFunction.getBasicBlocks().get(0);
        AllocaInst alloca = new AllocaInst(irType, node.name, null);
        entry.addFirst(alloca); // this will set alloca's parent to entry
        context.registerSymbol(node.symbol, alloca);
        if (irType instanceof IntType) {
            if (node.initVal != null) {
                Value initVal = node.initVal.accept(this);
                new StoreInst(initVal, alloca, context.currentBlock);
            }
        } else if (irType instanceof ArrType at) {
            if (node.initVal instanceof ArrayInitializer init) {
                List<Expr> values = init.values;
                int count = Math.min(values.size(), at.getNumElements());
                for (int i = 0; i < count; i++) {
                    Value val = values.get(i).accept(this);
                    Value index = new ConstInt(IntType.I32, i);
                    Value ptr = new GetElementPtrInst(alloca, List.of(ConstInt.ZERO, index), context.currentBlock);
                    new StoreInst(val, ptr, context.currentBlock);
                }
                for (int i = count; i < at.getNumElements(); i++) {
                    Value index = new ConstInt(IntType.I32, i);
                    Value ptr = new GetElementPtrInst(alloca, List.of(ConstInt.ZERO, index), context.currentBlock);
                    new StoreInst(ConstInt.ZERO, ptr, context.currentBlock);
                }
            }
        }
        return null;
    }

    @Override
    public Value visit(FuncDef node) {
        IrType retType = translate(node.funcType);
        List<IrType> paramTypes = node.params != null ? node.params.stream()
            .map(p -> p.isArray ? new ArrayType(p.type, ArrayType.UNSIZED) : p.type).map(this::translate).toList()
            : List.of();
        Function func = new Function("@" + node.name, new FuncType(retType, paramTypes), false);
        module.addFunction(func);
        context.registerSymbol(node.symbol, func);
        context.currentFunction = func;
        context.currentBlock = module.createBlock("entry", func);
        var args = func.getArguments();
        int size = (node.params != null) ? Math.min(node.params.size(), args.size()) : 0;
        for (int i = 0; i < size; i++) {
            Argument arg = args.get(i);
            FuncParam paramAst = node.params.get(i);
            AllocaInst alloca = new AllocaInst(arg.getType(), paramAst.name, context.currentBlock);
            new StoreInst(arg, alloca, context.currentBlock);
            context.registerSymbol(paramAst.symbol, alloca);
        }
        node.body.accept(this);
        if (node.funcType instanceof VoidType) {
            if (!context.blockTerminated()) {
                new ReturnInst(context.currentBlock);
            }
        }
        context.currentFunction = null;
        context.currentBlock = null;
        return null;
    }

    @Override
    public Value visit(BinaryExpr node) {
        if (node.op == TokenType.AND || node.op == TokenType.OR) {
            BasicBlock entry = context.currentFunction.getBasicBlocks().get(0);
            AllocaInst resultAddr = new AllocaInst(IntType.I1, "", null);
            entry.addFirst(resultAddr);

            BasicBlock rhs = module.createBlock("rhs", context.currentFunction);
            BasicBlock merge = module.createBlock("merge", context.currentFunction);

            Value lVal = node.left.accept(this);
            if (lVal.getType() instanceof IntType it && it.getBitWidth() == 32) {
                lVal = new IcmpInst(IcmpInst.CondCode.NE, lVal, ConstInt.ZERO, context.currentBlock);
            }
            if (node.op == TokenType.AND) {
                new StoreInst(new ConstInt(IntType.I1, 0), resultAddr, context.currentBlock);
                context.condJump(lVal, rhs, merge);
            } else {
                new StoreInst(new ConstInt(IntType.I1, 1), resultAddr, context.currentBlock);
                context.condJump(lVal, merge, rhs);
            }
            context.currentBlock = rhs;
            Value rVal = node.right.accept(this);
            if (rVal.getType() instanceof IntType it && it.getBitWidth() == 32) {
                rVal = new IcmpInst(IcmpInst.CondCode.NE, rVal, ConstInt.ZERO, context.currentBlock);
            }
            new StoreInst(rVal, resultAddr, context.currentBlock);
            context.jump(merge);
            context.currentBlock = merge;
            return new LoadInst(resultAddr, context.currentBlock);
        }
        Value left = node.left.accept(this);
        Value right = node.right.accept(this);
        return switch (node.op) {
            case PLUS -> new BinaryInst(OpCode.ADD, left, right, context.currentBlock);
            case MINU -> new BinaryInst(OpCode.SUB, left, right, context.currentBlock);
            case MULT -> new BinaryInst(OpCode.MUL, left, right, context.currentBlock);
            case DIV -> new BinaryInst(OpCode.SDIV, left, right, context.currentBlock);
            case MOD -> new BinaryInst(OpCode.SREM, left, right, context.currentBlock);
            case LSS -> new IcmpInst(IcmpInst.CondCode.SLT, left, right, context.currentBlock);
            case LEQ -> new IcmpInst(IcmpInst.CondCode.SLE, left, right, context.currentBlock);
            case GRE -> new IcmpInst(IcmpInst.CondCode.SGT, left, right, context.currentBlock);
            case GEQ -> new IcmpInst(IcmpInst.CondCode.SGE, left, right, context.currentBlock);
            case EQL -> new IcmpInst(IcmpInst.CondCode.EQ, left, right, context.currentBlock);
            case NEQ -> new IcmpInst(IcmpInst.CondCode.NE, left, right, context.currentBlock);
            default -> null;
        };
    }

    @Override
    public Value visit(LVal node) {
        if (node.symbol.isConst()) {
            var initValues = node.symbol.initialValues();
            if (node.indices.isEmpty()) {
                if (!initValues.isEmpty()) {
                    return new ConstInt(IntType.I32, initValues.get(0));
                }
                return ConstInt.ZERO;
            }
            Value indexVal = node.indices.get(0).accept(this);
            if (indexVal instanceof ConstInt idxConst) {
                int idx = idxConst.getValue();
                if (idx >= 0 && idx < initValues.size()) {
                    return new ConstInt(IntType.I32, initValues.get(idx));
                }
                return ConstInt.ZERO;
            }
        }
        Value ptr = getAddress(node);
        if (ptr == null) {
            return null;
        }
        if (ptr.getType() instanceof PtrType pt && pt.getPointeeType() instanceof ArrType) {
            return new GetElementPtrInst(ptr, List.of(ConstInt.ZERO, ConstInt.ZERO), context.currentBlock);
        } else {
            return new LoadInst(ptr, context.currentBlock);
        }
    }

    @Override
    public Value visit(AssignStmt node) {
        Value ptr = getAddress(node.lVal);
        if (ptr != null) {
            Value val = node.rVal.accept(this);
            new StoreInst(val, ptr, context.currentBlock);
        }
        return null;
    }

    @Override
    public Value visit(ReturnStmt node) {
        if (!context.blockTerminated()) {
            if (node.retVal != null) {
                new ReturnInst(node.retVal.accept(this), context.currentBlock);
            } else {
                new ReturnInst(context.currentBlock);
            }
        }
        return null;
    }

    @Override
    public Value visit(BlockStmt node) {
        node.items.forEach(stmt -> stmt.accept(this));
        return null;
    }

    @Override
    public Value visit(IntLiteral node) {
        return new ConstInt(IntType.I32, node.value);
    }

    @Override
    public Value visit(FuncParam node) {
        return null;
    }

    @Override
    public Value visit(ExprStmt node) {
        if (node.expr != null) {
            node.expr.accept(this);
        }
        return null;
    }

    @Override
    public Value visit(IfStmt node) {
        BasicBlock thenBlock = module.createBlock("if_then", context.currentFunction);
        BasicBlock elseBlock = (node.elseStmt != null) ? module.createBlock("if_else", context.currentFunction) : null;
        BasicBlock nextBlock = module.createBlock("if_next", context.currentFunction);
        Value condVal = node.cond.accept(this);
        Value boolVal = condVal;
        if (condVal.getType() instanceof IntType it && it.getBitWidth() == 32) {
            boolVal = new IcmpInst(IcmpInst.CondCode.NE, condVal, ConstInt.ZERO, context.currentBlock);
        }
        context.condJump(boolVal, thenBlock, Objects.requireNonNullElse(elseBlock, nextBlock));
        context.currentBlock = thenBlock;
        node.then.accept(this);
        context.jump(nextBlock);
        if (elseBlock != null) {
            context.currentBlock = elseBlock;
            node.elseStmt.accept(this);
            context.jump(nextBlock);
        }
        context.currentBlock = nextBlock;
        return null;
    }

    @Override
    public Value visit(ForLoopStmt node) {
        if (node.init != null) {
            node.init.forEach(stmt -> stmt.accept(this));
        }
        BasicBlock cond = module.createBlock("for_cond", context.currentFunction);
        BasicBlock body = module.createBlock("for_body", context.currentFunction);
        BasicBlock step = module.createBlock("for_step", context.currentFunction);
        BasicBlock exit = module.createBlock("for_exit", context.currentFunction);
        context.jump(cond);
        context.currentBlock = cond;
        if (node.cond != null) {
            Value condVal = node.cond.accept(this);
            Value boolVal = condVal;
            if (condVal.getType() instanceof IntType it && it.getBitWidth() == 32) {
                boolVal = new IcmpInst(IcmpInst.CondCode.NE, condVal, ConstInt.ZERO, context.currentBlock);
            }
            context.condJump(boolVal, body, exit);
        } else {
            context.jump(body);
        }
        context.enterLoop(step, exit);
        context.currentBlock = body;
        if (node.body != null) {
            node.body.accept(this);
        }
        context.jump(step);
        context.exitLoop();
        context.currentBlock = step;
        if (node.post != null) {
            node.post.forEach(stmt -> stmt.accept(this));
        }
        context.jump(cond);
        context.currentBlock = exit;
        return null;
    }

    @Override
    public Value visit(BreakStmt node) {
        context.currentLoop().ifPresent(loop -> new BrInst(loop.exit(), context.currentBlock));
        return null;
    }

    @Override
    public Value visit(ContinueStmt node) {
        context.currentLoop().ifPresent(loop -> new BrInst(loop.step(), context.currentBlock));
        return null;
    }

    @Override
    public Value visit(PrintfStmt node) {
        String rawFmt = node.formatStr.substring(1, node.formatStr.length() - 1);
        int argIdx = 0;
        int len = rawFmt.length();
        int i = 0;
        while (i < len) {
            int next = rawFmt.indexOf("%d", i);
            String literalContent;
            boolean hasSpec = false;
            if (next == -1) {
                literalContent = rawFmt.substring(i);
                i = len;
            } else {
                literalContent = rawFmt.substring(i, next);
                i = next + 2;
                hasSpec = true;
            }
            if (!literalContent.isEmpty()) {
                var strLiteral = module.createString(literalContent);
                var strPtr =
                    new GetElementPtrInst(strLiteral, List.of(ConstInt.ZERO, ConstInt.ZERO), context.currentBlock);
                var putstr = context.lookupBuiltin("putstr");
                putstr.ifPresent(p -> new CallInst(p, List.of(strPtr), context.currentBlock));
            }
            if (hasSpec && argIdx < node.args.size()) {
                Value val = node.args.get(argIdx++).accept(this);
                var putint = context.lookupBuiltin("putint");
                putint.ifPresent(p -> new CallInst(p, List.of(val), context.currentBlock));
            }
        }
        return null;
    }

    @Override
    public Value visit(UnaryExpr node) {
        Value operand = node.operand.accept(this);
        return switch (node.op) {
            case PLUS -> operand;
            // will never occur before an i1 type operand
            case MINU -> new BinaryInst(OpCode.SUB, ConstInt.ZERO, operand, context.currentBlock);
            case NOT -> {
                var zero = (operand.getType() instanceof IntType it && it.getBitWidth() == 1)
                    ? new ConstInt(IntType.I1, 0) : ConstInt.ZERO;
                yield new IcmpInst(IcmpInst.CondCode.EQ, operand, zero, context.currentBlock);
            }
            default -> null;
        };
    }

    @Override
    public Value visit(FuncCall node) {
        var funcValue = context.lookupSymbol(node.symbol);
        if (funcValue.isPresent() && funcValue.get() instanceof Function func) {
            var args = node.args.stream().map(arg -> arg.accept(this)).toList();
            return new CallInst(func, args, context.currentBlock);
        }
        return null;
    }

    @Override
    public Value visit(ArrayInitializer node) {
        return null;
    }

    private IrType translate(Type type) {
        if (type instanceof IntegerType) {
            return IntType.I32;
        }
        if (type instanceof VoidType) {
            return NoneType.VOID;
        }
        if (type instanceof ArrayType at) {
            if (at.isUnsized()) {
                return new PtrType(translate(at.elementType()));
            }
            return new ArrType(translate(at.elementType()), at.length());
        }
        if (type instanceof FunctionType ft) {
            IrType ret = translate(ft.returnType());
            List<IrType> params = ft.paramTypes().stream().map(this::translate).toList();
            return new FuncType(ret, params);
        }
        return IntType.I32;
    }

    private Value getAddress(LVal node) {
        return context.lookupSymbol(node.symbol).map(baseAddr -> {
            var current = baseAddr;
            var symType = node.symbol.type();
            if (symType instanceof ArrayType at && !node.indices.isEmpty()) {
                if (at.isUnsized()) {
                    var ptr = new LoadInst(current, context.currentBlock);
                    var index = node.indices.get(0).accept(this);
                    current = new GetElementPtrInst(ptr, List.of(index), context.currentBlock);
                } else {
                    var index = node.indices.get(0).accept(this);
                    current = new GetElementPtrInst(current, List.of(ConstInt.ZERO, index), context.currentBlock);
                }
            }
            return current;
        }).orElse(null);
    }
}
