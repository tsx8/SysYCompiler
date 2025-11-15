package top.tsxb.compiler.frontend.cst;

/**
 * The enum Cst type.
 */
public enum CstType {
    CompUnit, Decl, ConstDecl, BType, ConstDef, ConstInitVal, VarDecl, VarDef, InitVal, FuncDef, MainFuncDef, FuncType,
    FuncFParams, FuncFParam, Block, BlockItem, Stmt, AssignStmt, IfStmt, ForLoopStmt, BreakStmt, ContinueStmt,
    ReturnStmt, PrintfStmt, ExpStmt, ForStmt, Exp, Cond, LVal, PrimaryExp, Number, UnaryExp, FuncCall, UnaryOp,
    FuncRParams, MulExp, AddExp, RelExp, EqExp, LAndExp, LOrExp, ConstExp;

    @Override
    public String toString() {
        return String.format("<%s>", this.name());
    }

}
