package top.tsxb.compiler.ir;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import top.tsxb.compiler.ir.inst.Instruction;
import top.tsxb.compiler.ir.type.NoneType;
import top.tsxb.compiler.ir.value.Argument;
import top.tsxb.compiler.ir.value.BasicBlock;
import top.tsxb.compiler.ir.value.ConstString;
import top.tsxb.compiler.ir.value.Function;
import top.tsxb.compiler.ir.value.GlobalVariable;
import top.tsxb.compiler.ir.value.Value;

public class Module {
    private final Map<String, GlobalVariable> globalVariables = new LinkedHashMap<>();
    private final Map<String, Function> functions = new LinkedHashMap<>();
    private final Map<String, GlobalVariable> stringPool = new LinkedHashMap<>();

    public void addFunction(Function function) {
        functions.put(function.getName(), function);
    }

    public void addGlobalVariable(GlobalVariable gv) {
        while (globalVariables.containsKey(gv.getName())) {
            gv.setName(gv.getName() + "_1");
        }
        globalVariables.put(gv.getName(), gv);
    }

    public GlobalVariable createString(String literal) {
        if (stringPool.containsKey(literal)) {
            return stringPool.get(literal);
        }
        ConstString constString = new ConstString(literal);
        GlobalVariable gv =
            new GlobalVariable(".str", constString.getType(), true, constString);
        stringPool.put(literal, gv);
        return gv;
    }

    private void renameValue(Value v, Map<String, Integer> counterMap) {
        String originalName = v.getName();
        if (originalName == null) {
            originalName = "";
        }
        int cnt = counterMap.getOrDefault(originalName, 0);

        String newName;
        if (originalName.isEmpty()) {
            newName = String.valueOf(cnt);
        } else {
            newName = originalName + "_" + cnt;
        }

        v.setName(newName);
        counterMap.put(originalName, cnt + 1);
    }

    private void renameValues() {
        int strIdx = 0;
        for (GlobalVariable gv : stringPool.values()) {
            gv.setName(".str." + (++strIdx));
        }
        for (Function func : functions.values()) {
            if (func.isBuiltin) {
                continue;
            }
            Map<String, Integer> localNameMap = new HashMap<>();
            for (Argument arg : func.getArguments()) {
                renameValue(arg, localNameMap);
            }
            for (BasicBlock bb : func.getBasicBlocks()) {
                renameValue(bb, localNameMap);
                for (Instruction inst : bb.getInstructions()) {
                    if (inst.getType() instanceof NoneType) {
                        continue;
                    }
                    renameValue(inst, localNameMap);
                }
            }
        }
    }

    @Override
    public String toString() {
        renameValues();

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
