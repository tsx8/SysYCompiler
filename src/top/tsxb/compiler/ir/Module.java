package top.tsxb.compiler.ir;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import top.tsxb.compiler.ir.value.ConstString;
import top.tsxb.compiler.ir.value.Function;
import top.tsxb.compiler.ir.value.GlobalVariable;
import top.tsxb.compiler.ir.value.Value;

public class Module {
    private final Map<String, GlobalVariable> globalVariables = new LinkedHashMap<>();
    private final Map<String, Function> functions = new LinkedHashMap<>();
    private final Map<String, GlobalVariable> stringPool = new LinkedHashMap<>();
    private final Map<String, Integer> globalNameMap = new HashMap<>();

    public void addFunction(Function function) {
        resolveGlobalName(function);
        functions.put(function.getName(), function);
    }

    public void addGlobalVariable(GlobalVariable gv) {
        resolveGlobalName(gv);
        globalVariables.put(gv.getName(), gv);
    }

    public GlobalVariable createString(String literal) {
        if (stringPool.containsKey(literal)) {
            return stringPool.get(literal);
        }
        ConstString constString = new ConstString(literal);
        GlobalVariable gv =
            new GlobalVariable(".str", constString.getType(), true, constString);
        resolveGlobalName(gv);
        stringPool.put(literal, gv);
        return gv;
    }

    private void resolveGlobalName(Value value) {
        String originalName = value.getName();
        if (originalName == null || originalName.isEmpty()) {
            originalName = "anonymous";
            value.setName(originalName);
        }
        if (globalNameMap.containsKey(originalName)) {
            int count = globalNameMap.get(originalName);
            value.setName(originalName + "." + count);
            globalNameMap.put(originalName, count + 1);
        } else {
            globalNameMap.put(originalName, 1);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (GlobalVariable str : stringPool.values()) {
            sb.append(str).append(System.lineSeparator());
        }
        if (!stringPool.isEmpty()) {
            sb.append(System.lineSeparator());
        }
        for (GlobalVariable globalVariable : globalVariables.values()) {
            sb.append(globalVariable).append(System.lineSeparator());
        }
        if (!globalVariables.isEmpty()) {
            sb.append(System.lineSeparator());
        }
        for (Function function : functions.values()) {
            sb.append(function).append(System.lineSeparator());
        }

        return sb.toString();
    }
}
