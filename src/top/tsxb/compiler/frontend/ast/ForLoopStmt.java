package top.tsxb.compiler.frontend.ast;

import java.util.List;

public class ForLoopStmt extends Stmt {
    public final List<AssignStmt> init;
    public final Expr cond;
    public final List<AssignStmt> post;
    public final Stmt body;

    public ForLoopStmt(List<AssignStmt> init, Expr cond, List<AssignStmt> post, Stmt body) {
        this.init = init;
        this.cond = cond;
        this.post = post;
        this.body = body;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
