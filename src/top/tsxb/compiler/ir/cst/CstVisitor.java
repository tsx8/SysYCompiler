package top.tsxb.compiler.ir.cst;

public interface CstVisitor<T> {
    T visit(NonTerm node);

    T visit(Token node);
}
