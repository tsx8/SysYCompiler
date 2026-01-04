package top.tsxb.compiler.ir.structure;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import top.tsxb.compiler.ir.base.Value;

public class NameResolver {
    private final Map<String, Integer> nameMap = new LinkedHashMap<>();
    private final Set<String> usedNames = new LinkedHashSet<>();

    public void resolveName(Value value) {
        String nameHint = value.getName();
        if (nameHint == null || nameHint.isEmpty()) {
            nameHint = "anonymous";
        }

        String candidate = nameHint;
        if (usedNames.contains(candidate)) {
            int count = nameMap.getOrDefault(nameHint, 1);
            do {
                candidate = nameHint + "." + count;
                count++;
            } while (usedNames.contains(candidate));
            nameMap.put(nameHint, count);
        } else {
            if (!nameMap.containsKey(nameHint)) {
                nameMap.put(nameHint, 1);
            }
        }
        value.setName(candidate);
        usedNames.add(candidate);
    }
}
