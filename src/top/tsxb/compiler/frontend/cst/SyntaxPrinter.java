package top.tsxb.compiler.frontend.cst;

import static top.tsxb.compiler.frontend.cst.CstType.*;

import java.util.Set;

public class SyntaxPrinter implements CstVisitor<String> {
    private static final Set<CstType> OMITTED_NON_TERMINALS = Set.of(BlockItem, Decl, BType, AssignStmt, IfStmt,
        ForLoopStmt, BreakStmt, ContinueStmt, ReturnStmt, PrintfStmt, ExpStmt, FuncCall);
    private static final Set<CstType> BINARY_TYPES = Set.of(MulExp, AddExp, RelExp, EqExp, LAndExp, LOrExp);

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
