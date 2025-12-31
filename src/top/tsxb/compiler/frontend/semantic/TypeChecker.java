package top.tsxb.compiler.frontend.semantic;

import java.util.List;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;
import top.tsxb.compiler.frontend.semantic.ast.*;
import top.tsxb.compiler.frontend.semantic.sym.Symbol;
import top.tsxb.compiler.frontend.semantic.sym.SymbolTable;
import top.tsxb.compiler.frontend.semantic.type.ArrayType;
import top.tsxb.compiler.frontend.semantic.type.FunctionType;
import top.tsxb.compiler.frontend.semantic.type.IntegerType;
import top.tsxb.compiler.frontend.semantic.type.Type;
import top.tsxb.compiler.frontend.semantic.type.VoidType;

public class TypeChecker implements AstVisitor<Type> {
    private final SymbolTable symbolTable;
    private final ErrorReporter errorReporter;
    private final SymbolCollector symbolCollector;

    private FuncDef currentFuncDef = null;
    private int loopDepth = 0;

    public TypeChecker(SymbolTable symbolTable, ErrorReporter errorReporter) {
        this.symbolTable = symbolTable;
        this.errorReporter = errorReporter;
        this.symbolCollector = new SymbolCollector(symbolTable);
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
        Symbol funcSymbol = (Symbol)symbolCollector.visit(node);
        if (funcSymbol != null && !symbolTable.define(funcSymbol)) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("b"), node.name);
        }

        FuncDef previousFuncDef = currentFuncDef;
        this.currentFuncDef = node;
        symbolTable.enterScope();
        try {
            if (node.params != null) {
                for (FuncParam param : node.params) {
                    param.accept(this);
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
                    errorReporter.report(node.body.endLineNumber, ErrorType.fromCode("g"), node.name);
                }
            }
        } finally {
            symbolTable.exitScope();
            currentFuncDef = previousFuncDef;
        }
        return null;
    }

    @Override
    public Type visit(FuncParam node) {
        Symbol paramSymbol = (Symbol)symbolCollector.visit(node);
        if (!symbolTable.define(paramSymbol)) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("b"), node.name);
        }
        return node.type;
    }

    @Override
    public Type visit(VarDecl node) {
        Symbol symbol = (Symbol)symbolCollector.visit(node);
        if (!symbolTable.define(symbol)) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("b"), node.name);
        }
        if (node.dim != null) {
            node.dim.accept(this);
        }
        if (node.initVal != null) {
            node.initVal.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(BlockStmt node) {
        symbolTable.enterScope();
        try {
            for (AstNode item : node.items) {
                item.accept(this);
            }
        } finally {
            symbolTable.exitScope();
        }
        return null;
    }

    @Override
    public Type visit(AssignStmt node) {
        node.lVal.accept(this);
        node.rVal.accept(this);
        if (node.lVal.symbol != null && node.lVal.symbol.isConst()) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("h"), node.lVal.name);
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
            errorReporter.report(node.lineNumber, ErrorType.fromCode("m"), "break");
        }
        return null;
    }

    @Override
    public Type visit(ContinueStmt node) {
        if (loopDepth == 0) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("m"), "continue");
        }
        return null;
    }

    @Override
    public Type visit(ReturnStmt node) {
        Type rt = currentFuncDef.funcType;
        if (node.retVal != null) {
            if (rt instanceof VoidType) {
                errorReporter.report(node.lineNumber, ErrorType.fromCode("f"), currentFuncDef.name);
            } else if (rt instanceof IntegerType) {
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
            errorReporter.report(node.lineNumber, ErrorType.fromCode("l"), node.formatStr);
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
        Symbol symbol = symbolTable.lookup(node.name);
        if (symbol == null) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("c"), node.name);
            node.type = IntegerType.getInstance();
            return node.type;
        }

        if (!(symbol.type() instanceof FunctionType ft)) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("c"), node.name);
            node.type = IntegerType.getInstance();
            return node.type;
        }
        node.symbol = symbol;
        List<Type> formalParams = ft.paramTypes();
        List<Expr> actualArgs = node.args;
        if (actualArgs.size() != formalParams.size()) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("d"), node.name);
        } else {
            for (int i = 0; i < actualArgs.size(); i++) {
                Type actualType = actualArgs.get(i).accept(this);
                Type expectedType = formalParams.get(i);
                if (actualType == null) {
                    continue;
                }
                if (!actualType.isCastableTo(expectedType)) {
                    errorReporter.report(node.lineNumber, ErrorType.fromCode("e"), node.name);
                    break;
                }
            }
        }
        node.type = ft.returnType();
        return node.type;
    }

    @Override
    public Type visit(LVal node) {
        Symbol symbol = symbolTable.lookup(node.name);
        if (symbol == null) {
            errorReporter.report(node.lineNumber, ErrorType.fromCode("c"), node.name);
            node.type = IntegerType.getInstance();
            return node.type;
        }
        node.symbol = symbol;
        Type type = symbol.type();
        for (Expr index : node.indices) {
            index.accept(this);
            if (type instanceof ArrayType at) {
                type = at.elementType();
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
