package top.tsxb.compiler.ir.ast;

import java.util.List;

public class BlockStmt extends Stmt {
    public final List<AstNode> items;
    public int endLineNumber;

    public BlockStmt(List<AstNode> items) {
        this.items = items;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
