package top.tsxb.compiler.frontend.visitor;

import java.util.Set;

import top.tsxb.compiler.frontend.cst.CstNode;
import top.tsxb.compiler.frontend.cst.CstType;
import top.tsxb.compiler.frontend.cst.CstVisitor;
import top.tsxb.compiler.frontend.cst.NonTerm;
import top.tsxb.compiler.frontend.cst.Token;

public class SyntaxPrinter implements CstVisitor<String> {
    private static final Set<CstType> OMITTED_NON_TERMINALS = Set.of(CstType.BlockItem, CstType.Decl, CstType.BType);
    private static final Set<CstType> BINARY_TYPES =
        Set.of(CstType.MulExp, CstType.AddExp, CstType.RelExp, CstType.EqExp, CstType.LAndExp, CstType.LOrExp);

    @Override
    public String visit(Token node) {
        return node.toString() + System.lineSeparator();
    }

    @Override
    public String visit(NonTerm node) {
        final CstType type = node.type();
        final boolean isBinary = BINARY_TYPES.contains(type);

        StringBuilder sb = new StringBuilder();

        for (CstNode child : node.children()) {
            sb.append(child.accept(this));
            if (child instanceof NonTerm && isBinary) {
                sb.append(type.toString()).append(System.lineSeparator());
            }
        }

        if (!OMITTED_NON_TERMINALS.contains(type) && !isBinary) {
            sb.append(type.toString()).append(System.lineSeparator());
        }

        return sb.toString();
    }
}
