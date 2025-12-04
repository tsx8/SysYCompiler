package top.tsxb.compiler.ir.value;

import java.util.List;
import java.util.stream.Collectors;

import top.tsxb.compiler.ir.type.ArrType;

public class ConstArray extends Constant {
    private final List<Constant> values;

    public ConstArray(ArrType type, List<Constant> values) {
        super(type, "");
        this.values = List.copyOf(values);
    }

    @Override
    public String getRef() {
        String elements = values.stream().map(c -> c.getType().toString() + " " + c).collect(Collectors.joining(", "));
        return "[" + elements + "]";
    }
}
