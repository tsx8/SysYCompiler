package top.tsxb.compiler.frontend.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.tsxb.compiler.frontend.ast.AstNode;

public class Scope {
    private final Scope parent;
    private final List<Scope> children = new ArrayList<>();
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final int id;
    private final AstNode owner;

    public Scope(Scope parent, int id, AstNode owner) {
        this.parent = parent;
        this.id = id;
        this.owner = owner;
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

    public void addChild(Scope child) {
        this.children.add(child);
    }

    public Scope getParent() {
        return parent;
    }

    public int getId() {
        return id;
    }
}
