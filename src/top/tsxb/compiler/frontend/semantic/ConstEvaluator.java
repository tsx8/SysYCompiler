package top.tsxb.compiler.frontend.semantic;

import java.util.Optional;

import top.tsxb.compiler.ir.ast.*;
import top.tsxb.compiler.ir.symtab.Symbol;
import top.tsxb.compiler.ir.symtab.SymbolTable;

public record ConstEvaluator(SymbolTable symbolTable) implements AstVisitor<Optional<Integer>> {

    public Optional<Integer> evaluate(Expr node) {
        if (node == null) {
            return Optional.empty();
        }
        return node.accept(this);
    }

    @Override
    public Optional<Integer> visit(IntLiteral node) {
        return Optional.of(node.value);
    }

    @Override
    public Optional<Integer> visit(ArrayInitializer node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(LVal node) {
        if (!node.indices.isEmpty()) {
            return Optional.empty();
        }

        Symbol symbol = symbolTable.lookup(node.name);
        if (symbol != null && symbol.isConst() && symbol.constValue() != null) {
            return Optional.of(symbol.constValue());
        }

        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(UnaryExpr node) {
        Optional<Integer> operandVal = evaluate(node.operand);
        if (operandVal.isEmpty()) {
            return Optional.empty();
        }

        int val = operandVal.get();
        return switch (node.op) {
            case PLUS -> Optional.of(val);
            case MINU -> Optional.of(-val);
            case NOT -> Optional.of(val == 0 ? 1 : 0);
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Integer> visit(FuncParam node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(FuncCall node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(CompUnit node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(FuncDef node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(VarDecl node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(VarSpec node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(BlockStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(AssignStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(ExprStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(IfStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(ForLoopStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(BreakStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(ContinueStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(ReturnStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(PrintfStmt node) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> visit(BinaryExpr node) {
        Optional<Integer> left = evaluate(node.left);
        Optional<Integer> right = evaluate(node.right);

        if (left.isEmpty() || right.isEmpty()) {
            return Optional.empty();
        }

        int l = left.get();
        int r = right.get();

        var res = switch (node.op) {
            case PLUS -> l + r;
            case MINU -> l - r;
            case MULT -> l * r;
            case DIV -> l / r;
            case MOD -> l % r;
            case LSS -> l < r ? 1 : 0;
            case LEQ -> l <= r ? 1 : 0;
            case GRE -> l > r ? 1 : 0;
            case GEQ -> l >= r ? 1 : 0;
            case EQL -> l == r ? 1 : 0;
            case NEQ -> l != r ? 1 : 0;
            case AND -> (l != 0 && r != 0) ? 1 : 0;
            case OR -> (l != 0 || r != 0) ? 1 : 0;
            default -> null;
        };

        if (res == null) {
            return Optional.empty();
        }
        return Optional.of(res);
    }
}
