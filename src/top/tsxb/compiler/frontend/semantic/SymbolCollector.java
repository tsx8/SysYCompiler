package top.tsxb.compiler.frontend.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;
import top.tsxb.compiler.ir.ast.*;
import top.tsxb.compiler.ir.symtab.Symbol;
import top.tsxb.compiler.ir.symtab.SymbolTable;
import top.tsxb.compiler.ir.type.ArrayType;
import top.tsxb.compiler.ir.type.FunctionType;
import top.tsxb.compiler.ir.type.PointerType;
import top.tsxb.compiler.ir.type.Type;

public class SymbolCollector implements AstVisitor<Void> {
    private final SymbolTable symbolTable;
    private final ErrorReporter errorReporter;
    private final ConstEvaluator constEvaluator;

    public SymbolCollector(SymbolTable symbolTable, ErrorReporter errorReporter) {
        this.symbolTable = symbolTable;
        this.errorReporter = errorReporter;
        this.constEvaluator = new ConstEvaluator(symbolTable, errorReporter);
    }

    @Override
    public Void visit(CompUnit node) {
        for (Decl decl : node.decls) {
            decl.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(FuncDef node) {
        if (!node.name.equals("main")) {
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
            if (!symbolTable.define(funcSym)) {
                errorReporter.report(node.lineNumber, ErrorType.fromCode("b"), node.name);
            }
            node.symbol = funcSym;
        }
        symbolTable.enterScope(node);
        if (node.params != null) {
            for (FuncParam param : node.params) {
                param.accept(this);
            }
        }
        for (AstNode item : node.body.items) {
            item.accept(this);
        }
        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visit(VarDecl node) {
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
            if (!symbolTable.define(symbol)) {
                errorReporter.report(spec.lineNumber, ErrorType.fromCode("b"), spec.name);
            }
            spec.symbol = symbol;
        }
        return null;
    }

    @Override
    public Void visit(FuncParam node) {
        Type paramType = node.type;
        if (node.isArray) {
            paramType = new PointerType(paramType);
        }
        Symbol paramSym = new Symbol(node.name, paramType, symbolTable.getCurrentScopeId(), false, false,
            Collections.emptyList(), null);
        if (!symbolTable.define(paramSym)) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("b"), node.name);
        }
        node.symbol = paramSym;
        return null;
    }

    @Override
    public Void visit(BlockStmt node) {
        symbolTable.enterScope(node);
        for (AstNode item : node.items) {
            item.accept(this);
        }
        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visit(IfStmt node) {
        if (node.then != null) {
            node.then.accept(this);
        }
        if (node.elseStmt != null) {
            node.elseStmt.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(ForLoopStmt node) {
        if (node.body != null) {
            node.body.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(VarSpec node) {
        return null;
    }

    @Override
    public Void visit(AssignStmt node) {
        return null;
    }

    @Override
    public Void visit(ExprStmt node) {
        return null;
    }

    @Override
    public Void visit(BreakStmt node) {
        return null;
    }

    @Override
    public Void visit(ContinueStmt node) {
        return null;
    }

    @Override
    public Void visit(ReturnStmt node) {
        return null;
    }

    @Override
    public Void visit(PrintfStmt node) {
        return null;
    }

    @Override
    public Void visit(BinaryExpr node) {
        return null;
    }

    @Override
    public Void visit(UnaryExpr node) {
        return null;
    }

    @Override
    public Void visit(FuncCall node) {
        return null;
    }

    @Override
    public Void visit(LVal node) {
        return null;
    }

    @Override
    public Void visit(IntLiteral node) {
        return null;
    }

    @Override
    public Void visit(ArrayInitializer node) {
        return null;
    }
}
