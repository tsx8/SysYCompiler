package top.tsxb.compiler.frontend.semantic.type;

public interface Type {
    boolean isSame(Type other);

    boolean isCastableTo(Type other);
}
