package top.tsxb.compiler.frontend.semantic;

import java.util.Collections;
import java.util.List;

import top.tsxb.compiler.frontend.semantic.ast.*;
import top.tsxb.compiler.frontend.semantic.sym.Symbol;
import top.tsxb.compiler.frontend.semantic.sym.SymbolTable;
import top.tsxb.compiler.frontend.semantic.type.ArrayType;
import top.tsxb.compiler.frontend.semantic.type.FunctionType;
import top.tsxb.compiler.frontend.semantic.type.Type;

public record SymbolCollector(SymbolTable symbolTable) implements AstVisitor<Object> {

    @Override
    public Object visit(CompUnit node) {
        return null;
    }

    @Override
    public Object visit(FuncDef node) {
        List<Type> paramTypes = node.params == null ? Collections.emptyList()
            : node.params.stream().map(p -> p.isArray ? new ArrayType(p.type, ArrayType.UNSIZED) : p.type).toList();
        FunctionType funcType = new FunctionType(node.funcType, paramTypes);
        Symbol funcSym =
            new Symbol(node.name, funcType, symbolTable.getCurrentScopeId(), false, false, Collections.emptyList());
        node.symbol = funcSym;

        if (node.name.equals("main")) {
            return null;
        }
        return funcSym;
    }

    @Override
    public Object visit(VarDecl node) {
        Type finalType = node.type;
        if (node.dim != null) {
            int size = evaluate(node.dim);
            finalType = new ArrayType(finalType, size);
        }
        List<Integer> initValues = Collections.emptyList();
        if ((node.isConst || node.isStatic || symbolTable.getCurrentScopeId() == 1) && node.initVal != null) {
            initValues = flattenInitVal(node.initVal);
        }

        Symbol symbol = new Symbol(node.name, finalType, symbolTable().getCurrentScopeId(), node.isConst, node.isStatic,
            initValues);
        node.symbol = symbol;
        return symbol;
    }

    @Override
    public Object visit(FuncParam node) {
        Type paramType = node.isArray ? new ArrayType(node.type, ArrayType.UNSIZED) : node.type;
        Symbol paramSym =
            new Symbol(node.name, paramType, symbolTable.getCurrentScopeId(), false, false, Collections.emptyList());
        node.symbol = paramSym;
        return paramSym;
    }

    private List<Integer> flattenInitVal(Expr initVal) {
        if (initVal instanceof ArrayInitializer arrInit) {
            return arrInit.values.stream().flatMap(v -> flattenInitVal(v).stream()).toList();
        } else {
            return List.of(evaluate(initVal));
        }
    }

    private Integer evaluate(Expr expr) {
        if (expr == null)
            return 0;
        if (expr instanceof IntLiteral n) {
            return n.value;
        }
        if (expr instanceof UnaryExpr u) {
            int val = evaluate(u.operand);
            return switch (u.op) {
                case PLUS -> val;
                case MINU -> -val;
                case NOT -> val == 0 ? 1 : 0;
                default -> 0;
            };
        }
        if (expr instanceof BinaryExpr b) {
            int l = evaluate(b.left);
            int r = evaluate(b.right);
            return switch (b.op) {
                case PLUS -> l + r;
                case MINU -> l - r;
                case MULT -> l * r;
                case DIV -> r != 0 ? l / r : 0;
                case MOD -> r != 0 ? l % r : 0;
                case LSS -> l < r ? 1 : 0;
                case LEQ -> l <= r ? 1 : 0;
                case GRE -> l > r ? 1 : 0;
                case GEQ -> l >= r ? 1 : 0;
                case EQL -> l == r ? 1 : 0;
                case NEQ -> l != r ? 1 : 0;
                case AND -> (l != 0 && r != 0) ? 1 : 0;
                case OR -> (l != 0 || r != 0) ? 1 : 0;
                default -> 0;
            };
        }
        if (expr instanceof LVal lVal) {
            Symbol sym = symbolTable.lookup(lVal.name);
            if (sym == null || !sym.isConst() || sym.initialValues().isEmpty()) {
                return 0;
            }
            int offset = !lVal.indices.isEmpty() ? evaluate(lVal.indices.get(0)) : 0;
            if (offset >= 0 && offset < sym.initialValues().size()) {
                return sym.initialValues().get(offset);
            }
        }
        return 0;
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
