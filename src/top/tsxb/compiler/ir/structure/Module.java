package top.tsxb.compiler.ir.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.tsxb.compiler.ir.base.Value;
import top.tsxb.compiler.ir.constant.ConstString;

public class Module {
    private final List<GlobalVariable> globalList = new ArrayList<>();
    private final List<Function> functionList = new ArrayList<>();
    private final Map<String, GlobalValue> symbolMap = new HashMap<>();
    private final NameResolver nameResolver = new NameResolver();

    public List<GlobalVariable> getGlobalList() {
        return globalList;
    }

    public List<Function> getFunctionList() {
        return functionList;
    }

    public void addFunction(Function function) {
        resolveGlobalName(function);
        functionList.add(function);
        symbolMap.put(function.getName(), function);
    }

    public void addGlobalVariable(GlobalVariable gv) {
        resolveGlobalName(gv);
        globalList.add(gv);
        symbolMap.put(gv.getName(), gv);
    }

    public GlobalValue getNamedGlobal(String name) {
        return symbolMap.get(name);
    }

    public GlobalVariable createString(String literal) {
        ConstString constString = new ConstString(literal);
        GlobalVariable gv =
            new GlobalVariable(".str", constString.getType(), true, constString, GlobalValue.Linkage.INTERNAL);
        addGlobalVariable(gv);
        return gv;
    }

    private void resolveGlobalName(Value value) {
        nameResolver.resolveName(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (GlobalVariable gv : globalList) {
            sb.append(gv).append(System.lineSeparator());
        }
        if (!globalList.isEmpty()) {
            sb.append(System.lineSeparator());
        }

        for (Function func : functionList) {
            sb.append(func).append(System.lineSeparator());
        }

        return sb.toString();
    }
}
