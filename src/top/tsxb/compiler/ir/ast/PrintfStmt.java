package top.tsxb.compiler.ir.ast;

import java.util.List;

public class PrintfStmt extends Stmt {
    public final String formatStr;
    public final List<Expr> args;

    public PrintfStmt(String formatStr, List<Expr> args) {
        this.formatStr = formatStr;
        this.args = args;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
