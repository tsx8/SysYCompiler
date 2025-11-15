package top.tsxb.compiler.frontend.ast;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;
import top.tsxb.compiler.frontend.semantic.ArrayType;
import top.tsxb.compiler.frontend.semantic.FunctionType;
import top.tsxb.compiler.frontend.semantic.IntegerType;
import top.tsxb.compiler.frontend.semantic.PointerType;
import top.tsxb.compiler.frontend.semantic.Scope;
import top.tsxb.compiler.frontend.semantic.Symbol;
import top.tsxb.compiler.frontend.semantic.SymbolTable;
import top.tsxb.compiler.frontend.semantic.Type;
import top.tsxb.compiler.frontend.semantic.VoidType;

public class TypeChecker implements AstVisitor<Type> {
    private final SymbolTable symbolTable;
    private final ErrorReporter errorReporter;

    private FuncDef currentFuncDef = null;
    private int loopDepth = 0;
    private Scope currentScope;

    public TypeChecker(SymbolTable symbolTable, ErrorReporter errorReporter) {
        this.symbolTable = symbolTable;
        this.errorReporter = errorReporter;
        this.currentScope = symbolTable.getRootScope();
    }

    @Override
    public Type visit(CompUnit node) {
        for (Decl decl : node.decls) {
            decl.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(FuncDef node) {
        FuncDef previousFuncDef = currentFuncDef;
        this.currentFuncDef = node;
        Scope previousScope = currentScope;
        this.currentScope = symbolTable.getScope(node);
        try {
            if (node.params != null) {
                for (VarDecl varDecl : node.params) {
                    varDecl.accept(this);
                }
            }
            for (AstNode item : node.body.items) {
                item.accept(this);
            }
            if (node.funcType instanceof IntegerType) {
                boolean hasReturn = false;
                if (!node.body.items.isEmpty()) {
                    AstNode last = node.body.items.get(node.body.items.size() - 1);
                    if (last instanceof ReturnStmt) {
                        hasReturn = true;
                    }
                }
                if (!hasReturn) {
                    errorReporter.report(node.body.endLineNumber, ErrorType.INT_FUNC_MISSING_RETURN, node.name);
                }
            }
        } finally {
            currentFuncDef = previousFuncDef;
            currentScope = previousScope;
        }
        return null;
    }

    @Override
    public Type visit(VarDecl node) {
        if (node.varSpecs != null) {
            for (VarSpec varSpec : node.varSpecs) {
                varSpec.accept(this);
            }
        }
        return null;
    }

    @Override
    public Type visit(VarSpec node) {
        if (node.initVal != null) {
            node.initVal.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(BlockStmt node) {
        Scope previousScope = currentScope;
        currentScope = symbolTable.getScope(node);
        for (AstNode item : node.items) {
            item.accept(this);
        }
        currentScope = previousScope;
        return null;
    }

    @Override
    public Type visit(AssignStmt node) {
        node.lVal.accept(this);
        node.rVal.accept(this);
        if (node.lVal.symbol != null && node.lVal.symbol.isConst()) {
            errorReporter.report(node.lineNumber, ErrorType.MODIFY_CONST, node.lVal.name);
        }
        return null;
    }

    @Override
    public Type visit(ExprStmt node) {
        if (node.expr != null) {
            node.expr.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(IfStmt node) {
        node.cond.accept(this);
        node.then.accept(this);
        if (node.elseStmt != null) {
            node.elseStmt.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(ForLoopStmt node) {
        this.loopDepth++;
        try {
            if (node.init != null) {
                for (AssignStmt assignStmt : node.init) {
                    assignStmt.accept(this);
                }
            }
            if (node.cond != null) {
                node.cond.accept(this);
            }
            if (node.post != null) {
                for (AssignStmt assignStmt : node.post) {
                    assignStmt.accept(this);
                }
            }
            node.body.accept(this);
        } finally {
            this.loopDepth--;
        }
        return null;
    }

    @Override
    public Type visit(BreakStmt node) {
        if (loopDepth == 0) {
            errorReporter.report(node.lineNumber, ErrorType.BREAK_CONTINUE_OUTSIDE_LOOP, "break");
        }
        return null;
    }

    @Override
    public Type visit(ContinueStmt node) {
        if (loopDepth == 0) {
            errorReporter.report(node.lineNumber, ErrorType.BREAK_CONTINUE_OUTSIDE_LOOP, "continue");
        }
        return null;
    }

    @Override
    public Type visit(ReturnStmt node) {
        Type rt = currentFuncDef.funcType;
        if (rt instanceof VoidType) {
            if (node.retVal != null) {
                errorReporter.report(node.lineNumber, ErrorType.VOID_FUNC_WITH_RETURN_VALUE, currentFuncDef.name);
            }
        } else if (rt instanceof IntegerType) {
            if (node.retVal != null) {
                node.retVal.accept(this);
            }
        }
        return null;
    }

    @Override
    public Type visit(PrintfStmt node) {
        int formatSpecs = 0;
        for (int i = 0; i < node.formatStr.length() - 1; i++) {
            if (node.formatStr.charAt(i) == '%' && node.formatStr.charAt(i + 1) == 'd') {
                formatSpecs++;
            }
        }
        if (formatSpecs != node.args.size()) {
            errorReporter.report(node.lineNumber, ErrorType.PRINTF_ARG_COUNT_MISMATCH, node.formatStr);
        }
        for (Expr arg : node.args) {
            arg.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(BinaryExpr node) {
        node.left.accept(this);
        node.right.accept(this);
        node.type = IntegerType.getInstance();
        return node.type;
    }

    @Override
    public Type visit(UnaryExpr node) {
        node.operand.accept(this);
        node.type = IntegerType.getInstance();
        return node.type;
    }

    @Override
    public Type visit(FuncCall node) {
        Symbol symbol = currentScope.lookup(node.name);
        if (symbol == null) {
            errorReporter.report(node.lineNumber, ErrorType.NAME_NOT_DEFINED, node.name);
            node.type = IntegerType.getInstance();
            return node.type;
        }

        if (!(symbol.type() instanceof FunctionType ft)) {
            errorReporter.report(node.lineNumber, ErrorType.NAME_NOT_DEFINED, node.name);
            node.type = IntegerType.getInstance();
            return node.type;
        }
        node.symbol = symbol;
        if (node.args.size() != ft.paramTypes().size()) {
            errorReporter.report(node.lineNumber, ErrorType.FUNC_ARG_COUNT_MISMATCH, node.name);
        } else {
            for (int i = 0; i < node.args.size(); i++) {
                Type actualType = node.args.get(i).accept(this);
                Type expectedType = ft.paramTypes().get(i);
                if (actualType == null) {
                    continue;
                }
                boolean isArgArray = actualType instanceof ArrayType || actualType instanceof PointerType;
                boolean isParamArray = expectedType instanceof PointerType;
                if (isArgArray != isParamArray) {
                    errorReporter.report(node.lineNumber, ErrorType.FUNC_ARG_TYPE_MISMATCH, node.name);
                    break;
                }
            }
        }
        node.type = ft.returnType();
        return node.type;
    }

    @Override
    public Type visit(LVal node) {
        Symbol symbol = currentScope.lookup(node.name);
        if (symbol == null) {
            errorReporter.report(node.lineNumber, ErrorType.NAME_NOT_DEFINED, node.name);
            node.type = IntegerType.getInstance();
            return node.type;
        }
        node.symbol = symbol;
        Type type = symbol.type();
        for (Expr index : node.indices) {
            index.accept(this);
            if (type instanceof ArrayType at) {
                type = at.elementType();
            } else if (type instanceof PointerType pt) {
                type = pt.baseType();
            }
        }
        node.type = type;
        return node.type;
    }

    @Override
    public Type visit(IntLiteral node) {
        node.type = IntegerType.getInstance();
        return node.type;
    }

    @Override
    public Type visit(ArrayInitializer node) {
        for (Expr val : node.values) {
            val.accept(this);
        }
        return null;
    }
}
