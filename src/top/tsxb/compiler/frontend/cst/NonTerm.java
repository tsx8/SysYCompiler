package top.tsxb.compiler.frontend.cst;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Non term.
 */
public final class NonTerm extends CstNode {
    private final CstType type;
    private final List<CstNode> children = new ArrayList<>();

    @Override
    public <T> T accept(CstVisitor<T> visitor) {
        return visitor.visit(this);
    }

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

    public CstType type() {
        return type;
    }

    public List<CstNode> children() {
        return children;
    }
}
