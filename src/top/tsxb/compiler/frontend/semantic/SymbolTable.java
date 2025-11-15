package top.tsxb.compiler.frontend.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import top.tsxb.compiler.frontend.ast.AstNode;

public class SymbolTable {
    private final List<Symbol> symbols = new ArrayList<>();
    private final Map<AstNode, Scope> nodeToScopeMap = new HashMap<>();
    private final Scope rootScope;
    private Scope currentScope;
    private int scopeCounter = 0;

    public SymbolTable() {
        scopeCounter++;
        this.rootScope = new Scope(null, scopeCounter, null);
        this.currentScope = rootScope;
        FunctionType getintType = new FunctionType(IntegerType.getInstance(), Collections.emptyList());
        Symbol getintSymbol = new Symbol("getint", getintType, 1, false, false, Collections.emptyList(), null);
        this.rootScope.define(getintSymbol);
    }

    public void enterScope(AstNode node) {
        scopeCounter++;
        Scope newScope = new Scope(currentScope, scopeCounter, node);
        currentScope.addChild(newScope);
        currentScope = newScope;
        nodeToScopeMap.put(node, newScope);
    }

    public void exitScope() {
        if (currentScope.getParent() != null) {
            currentScope = currentScope.getParent();
        }
    }

    public boolean define(Symbol symbol) {
        if (currentScope.define(symbol)) {
            symbols.add(symbol);
            return true;
        }
        return false;
    }

    public Symbol lookup(String name) {
        if (currentScope != null) {
            return currentScope.lookup(name);
        }
        return null;
    }

    public Scope getScope(AstNode node) {
        return nodeToScopeMap.get(node);
    }

    public Scope getRootScope() {
        return rootScope;
    }

    public int getCurrentScopeId() {
        return currentScope.getId();
    }

    public String getOutput() {
        return symbols.stream().sorted(Comparator.comparingInt(Symbol::scopeLevel))
            .map(Symbol::toString).collect(Collectors.joining(System.lineSeparator()));
    }
}
