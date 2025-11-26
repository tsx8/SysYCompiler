package top.tsxb.compiler.frontend.semantic.sym;

import java.util.HashMap;
import java.util.Map;

public class Scope {
    private final Scope parent;
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final int id;

    public Scope(Scope parent, int id) {
        this.parent = parent;
        this.id = id;
    }

    public boolean define(Symbol symbol) {
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    public Symbol lookup(String name) {
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return symbol;
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return null;
    }

    public Scope getParent() {
        return parent;
    }

    public int getId() {
        return id;
    }
}
