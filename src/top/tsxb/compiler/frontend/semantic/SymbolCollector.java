package top.tsxb.compiler.frontend.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import top.tsxb.compiler.frontend.semantic.ast.*;
import top.tsxb.compiler.frontend.semantic.sym.Symbol;
import top.tsxb.compiler.frontend.semantic.sym.SymbolTable;
import top.tsxb.compiler.frontend.semantic.type.ArrayType;
import top.tsxb.compiler.frontend.semantic.type.FunctionType;
import top.tsxb.compiler.frontend.semantic.type.PointerType;
import top.tsxb.compiler.frontend.semantic.type.Type;

public class SymbolCollector implements AstVisitor<Object> {
    private final SymbolTable symbolTable;
    private final ConstEvaluator constEvaluator;

    public SymbolCollector(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.constEvaluator = new ConstEvaluator(symbolTable);
    }

    @Override
    public Object visit(CompUnit node) {
        return null;
    }

    @Override
    public Object visit(FuncDef node) {
        List<Type> paramTypes = new ArrayList<>();
        if (node.params != null) {
            for (FuncParam param : node.params) {
                if (param.isArray) {
                    paramTypes.add(new PointerType(param.type));
                } else {
                    paramTypes.add(param.type);
                }
            }
        }
        FunctionType funcType = new FunctionType(node.funcType, paramTypes);

        Symbol funcSym = new Symbol(node.name, funcType, symbolTable.getCurrentScopeId(), false, false,
            Collections.emptyList(), null);
        node.symbol = funcSym;
        if (node.name.equals("main")) {
            return null;
        }
        return funcSym;
    }

    @Override
    public Object visit(VarDecl node) {
        List<Symbol> symbols = new ArrayList<>();
        for (VarSpec spec : node.varSpecs) {
            List<Integer> dims = new ArrayList<>();
            Type finalType = node.type;
            if (spec.dims != null) {
                for (Expr dimExpr : spec.dims) {
                    Optional<Integer> dimVal = constEvaluator.evaluate(dimExpr);
                    dimVal.ifPresent(dims::add);
                }
                Collections.reverse(dims);
                for (Integer dim : dims) {
                    finalType = new ArrayType(finalType, dim);
                }
                Collections.reverse(dims);
            }
            Integer constValue = null;
            if (node.isConst && spec.initVal != null) {
                constValue = constEvaluator.evaluate(spec.initVal).orElse(null);
            }

            Symbol symbol = new Symbol(spec.name, finalType, symbolTable.getCurrentScopeId(), node.isConst,
                node.isStatic, dims, constValue);
            spec.symbol = symbol;
            symbols.add(symbol);
        }
        return symbols;
    }

    @Override
    public Object visit(FuncParam node) {
        Type paramType = node.type;
        if (node.isArray) {
            paramType = new PointerType(paramType);
        }
        Symbol paramSym = new Symbol(node.name, paramType, symbolTable.getCurrentScopeId(), false, false,
            Collections.emptyList(), null);
        node.symbol = paramSym;
        return paramSym;
    }

    @Override
    public Object visit(BlockStmt node) {
        return null;
    }

    @Override
    public Object visit(IfStmt node) {
        return null;
    }

    @Override
    public Object visit(ForLoopStmt node) {
        return null;
    }

    @Override
    public Object visit(VarSpec node) {
        return null;
    }

    @Override
    public Object visit(AssignStmt node) {
        return null;
    }

    @Override
    public Object visit(ExprStmt node) {
        return null;
    }

    @Override
    public Object visit(BreakStmt node) {
        return null;
    }

    @Override
    public Object visit(ContinueStmt node) {
        return null;
    }

    @Override
    public Object visit(ReturnStmt node) {
        return null;
    }

    @Override
    public Object visit(PrintfStmt node) {
        return null;
    }

    @Override
    public Object visit(BinaryExpr node) {
        return null;
    }

    @Override
    public Object visit(UnaryExpr node) {
        return null;
    }

    @Override
    public Object visit(FuncCall node) {
        return null;
    }

    @Override
    public Object visit(LVal node) {
        return null;
    }

    @Override
    public Object visit(IntLiteral node) {
        return null;
    }

    @Override
    public Object visit(ArrayInitializer node) {
        return null;
    }
}
