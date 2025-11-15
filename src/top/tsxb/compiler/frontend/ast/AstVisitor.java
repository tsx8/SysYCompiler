package top.tsxb.compiler.frontend.ast;

public interface AstVisitor<T> {
    T visit(CompUnit node);
    T visit(FuncDef node);
    T visit(FuncParam node);
    T visit(VarDecl node);
    T visit(VarSpec node);

    T visit(BlockStmt node);
    T visit(AssignStmt node);
    T visit(ExprStmt node);
    T visit(IfStmt node);
    T visit(ForLoopStmt node);
    T visit(BreakStmt node);
    T visit(ContinueStmt node);
    T visit(ReturnStmt node);
    T visit(PrintfStmt node);

    T visit(BinaryExpr node);
    T visit(UnaryExpr node);
    T visit(FuncCall node);
    T visit(LVal node);
    T visit(IntLiteral node);
    T visit(ArrayInitializer node);
}
