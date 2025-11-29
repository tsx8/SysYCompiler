package top.tsxb.compiler.ir.type;

public class ArrType extends IrType {
    private final IrType elementType;
    private final int numElements;

    public ArrType(IrType elementType, int numElements) {
        this.elementType = elementType;
        this.numElements = numElements;
    }

    public IrType getElementType() {
        return elementType;
    }

    public int getNumElements() {
        return numElements;
    }

    public int countScalars() {
        return countScalars(elementType);
    }

    private int countScalars(IrType it) {
        if (it instanceof ArrType at) {
            return at.getNumElements() * countScalars(at.getElementType());
        }
        return 1;
    }

    @Override
    public String toString() {
        return "[" + numElements + " x " + elementType.toString() + "]";
    }
}
