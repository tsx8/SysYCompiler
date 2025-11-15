package top.tsxb.compiler.frontend.semantic;

public record ArrayType(Type elementType, int numElements) implements Type {
    @Override
    public String toString() {
        return String.format("[%d x %s]", numElements, elementType.toString());
    }
}
