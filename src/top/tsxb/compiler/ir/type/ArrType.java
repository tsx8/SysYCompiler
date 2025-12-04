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

    @Override
    public String toString() {
        return "[" + numElements + " x " + elementType + "]";
    }
}
