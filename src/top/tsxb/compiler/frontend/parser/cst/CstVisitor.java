package top.tsxb.compiler.frontend.parser.cst;

public interface CstVisitor<T> {
    T visit(NonTerm node);

    T visit(Token node);
}
