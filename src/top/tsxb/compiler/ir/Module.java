package top.tsxb.compiler.ir;

import java.util.LinkedHashMap;
import java.util.Map;

import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.ConstString;
import top.tsxb.compiler.ir.value.Function;
import top.tsxb.compiler.ir.value.GlobalVariable;

public class Module {
    private final Map<String, GlobalVariable> globalVariables = new LinkedHashMap<>();
    private final Map<String, Function> functions = new LinkedHashMap<>();
    private final Map<String, GlobalVariable> stringPool = new LinkedHashMap<>();

    private int globalLabelCounter = 0;
    private int stringLiteralCounter = 0;

    public void addFunction(Function function) {
        functions.put(function.getName(), function);
    }

    public void addGlobalVariable(GlobalVariable globalVariable) {
        globalVariables.put(globalVariable.getName(), globalVariable);
    }

    public GlobalVariable createString(String literal) {
        if (stringPool.containsKey(literal)) {
            return stringPool.get(literal);
        }
        ConstString constString = new ConstString(literal);
        GlobalVariable gv =
        new GlobalVariable(".str." + (++stringLiteralCounter), constString.getType(), true, constString);
        stringPool.put(literal, gv);
        return gv;
    }

    public BasicBlock createBlock(String hint, Function currentFunction) {
        String name = hint + "_" + (++globalLabelCounter);
        return new BasicBlock(name, currentFunction);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (GlobalVariable str : stringPool.values()) {
            sb.append(str.toString()).append(System.lineSeparator());
        }
        if (!stringPool.isEmpty()) {
            sb.append(System.lineSeparator());
        }
        for (GlobalVariable globalVariable : globalVariables.values()) {
            sb.append(globalVariable.toString()).append(System.lineSeparator());
        }
        if (!globalVariables.isEmpty()) {
            sb.append(System.lineSeparator());
        }
        for (Function function : functions.values()) {
            sb.append(function.toString()).append(System.lineSeparator());
        }

        return sb.toString();
    }
}
