package top.tsxb.compiler.frontend.cst;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The type Non term.
 */
public final class NonTerm extends CstNode {
    private static final Set<CstType> OMITTED_NON_TERMINALS = Set.of(CstType.BlockItem, CstType.Decl, CstType.BType);
    private final CstType type;
    private final List<CstNode> children = new ArrayList<>();

    /**
     * Instantiates a new Non term.
     *
     * @param type the type
     */
    public NonTerm(CstType type) {
        this.type = type;
    }

    /**
     * Add.
     *
     * @param child the child
     */
    public void add(CstNode child) {
        children.add(child);
    }

    public CstType getType() {
        return type;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (CstNode child : children) {
            sb.append(child.toString());
        }

        if (!OMITTED_NON_TERMINALS.contains(type)) {
            sb.append("<").append(type.name()).append(">").append(System.lineSeparator());
        }
        return sb.toString();
    }
}
