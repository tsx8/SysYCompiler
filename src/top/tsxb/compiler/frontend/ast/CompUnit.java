package top.tsxb.compiler.frontend.ast;

import java.util.List;

public class CompUnit extends AstNode {
    public final List<Decl> decls;

    public CompUnit(List<Decl> decls) {
        this.decls = decls;
    }

    @Override
    public <T> T accept(AstVisitor<T> v) {
        return v.visit(this);
    }
}
