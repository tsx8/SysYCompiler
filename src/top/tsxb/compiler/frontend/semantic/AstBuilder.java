package top.tsxb.compiler.frontend.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import top.tsxb.compiler.ir.ast.*;
import top.tsxb.compiler.ir.cst.CstNode;
import top.tsxb.compiler.ir.cst.CstType;
import top.tsxb.compiler.ir.cst.CstVisitor;
import top.tsxb.compiler.ir.cst.NonTerm;
import top.tsxb.compiler.ir.cst.Token;
import top.tsxb.compiler.ir.cst.TokenType;
import top.tsxb.compiler.ir.type.IntegerType;
import top.tsxb.compiler.ir.type.Type;
import top.tsxb.compiler.ir.type.VoidType;

public class AstBuilder implements CstVisitor<Object> {
    public CompUnit build(CstNode node) {
        return (CompUnit)node.accept(this);
    }

    @Override
    public Object visit(Token node) {
        return node;
    }

    @Override
    public Object visit(NonTerm node) {
        return switch (node.type()) {
            // Top Level
            case CompUnit -> buildCompUnit(node);
            case FuncDef -> buildFuncDef(node);
            case MainFuncDef -> buildMainFuncDef(node);
            // Decl
            case ConstDecl, VarDecl -> buildVarDecl(node);
            case ConstDef, VarDef -> buildVarSpec(node);
            case ConstInitVal, InitVal -> buildInitVal(node);
            case FuncFParams -> buildFuncFParams(node);
            case FuncFParam -> buildFuncFParam(node);
            // Stmt
            case Block -> buildBlockStmt(node);
            case IfStmt -> buildIfStmt(node);
            case ForLoopStmt -> buildForLoopStmt(node);
            case ForStmt -> buildForStmt(node);
            case AssignStmt -> buildAssignStmt(node);
            case ReturnStmt -> buildReturnStmt(node);
            case PrintfStmt -> buildPrintfStmt(node);
            case ExpStmt -> buildExprStmt(node);
            case BreakStmt -> {
                BreakStmt bs = new BreakStmt();
                Cst.find(node, TokenType.BREAKTK).ifPresent(token -> bs.lineNumber = token.line());
                yield bs;
            }
            case ContinueStmt -> {
                ContinueStmt cs = new ContinueStmt();
                Cst.find(node, TokenType.CONTINUETK).ifPresent(token -> cs.lineNumber = token.line());
                yield cs;
            }
            // Expr
            case AddExp, MulExp, RelExp, EqExp, LAndExp, LOrExp -> buildBinaryExpr(node);
            case UnaryExp -> buildUnaryExpr(node);
            case PrimaryExp -> buildPrimaryExpr(node);
            case LVal -> buildLVal(node);
            case Number -> buildNumber(node);
            case FuncRParams -> buildFuncRParams(node);
            case FuncCall -> buildFuncCall(node);
            case BType -> Cst.find(node, TokenType.INTTK).map(t -> IntegerType.getInstance()).orElse(null);
            case FuncType -> {
                if (Cst.has(node, TokenType.VOIDTK)) {
                    yield VoidType.getInstance();
                } else {
                    yield IntegerType.getInstance();
                }
            }
            case Decl, Stmt, Exp, Cond, ConstExp, BlockItem, UnaryOp -> node.children().get(0).accept(this);
        };
    }

    private CompUnit buildCompUnit(NonTerm node) {
        List<Decl> decls = node.children().stream().map(child -> (Decl)child.accept(this)).toList();
        CompUnit compUnit = new CompUnit(decls);
        if (!node.children().isEmpty()) {
            Cst.findFirstToken(node.children().get(0)).ifPresent(t -> compUnit.lineNumber = t.line());
        }
        return compUnit;
    }

    @SuppressWarnings("unchecked")
    private FuncDef buildFuncDef(NonTerm node) {
        Type funcType = Cst.build(node, CstType.FuncType, n -> (Type)n.accept(this));
        String name = Cst.lexeme(node, TokenType.IDENFR);
        List<FuncParam> params = Cst.buildOpt(node, CstType.FuncFParams, n -> (List<FuncParam>)n.accept(this))
            .orElse(Collections.emptyList());
        BlockStmt body = Cst.build(node, CstType.Block, n -> (BlockStmt)n.accept(this));
        FuncDef funcDef = new FuncDef(funcType, name, params, body);
        Cst.find(node, TokenType.IDENFR).ifPresent(token -> funcDef.lineNumber = token.line());
        return funcDef;
    }

    private FuncDef buildMainFuncDef(NonTerm node) {
        BlockStmt body = Cst.build(node, CstType.Block, n -> (BlockStmt)n.accept(this));
        FuncDef funcDef = new FuncDef(IntegerType.getInstance(), "main", Collections.emptyList(), body);
        Cst.find(node, TokenType.MAINTK).ifPresent(token -> funcDef.lineNumber = token.line());
        return funcDef;
    }

    private List<FuncParam> buildFuncFParams(NonTerm node) {
        return Cst.buildAll(node, CstType.FuncFParam, n -> (FuncParam)n.accept(this));
    }

    private FuncParam buildFuncFParam(NonTerm node) {
        Type type = Cst.build(node, CstType.BType, n -> (Type)n.accept(this));
        String name = Cst.lexeme(node, TokenType.IDENFR);
        boolean isArray = Cst.has(node, TokenType.LBRACK);
        FuncParam funcParam = new FuncParam(type, name, isArray);
        Cst.find(node, TokenType.IDENFR).ifPresent(token -> funcParam.lineNumber = token.line());
        return funcParam;
    }

    private VarDecl buildVarDecl(NonTerm node) {
        boolean isConst = node.type() == CstType.ConstDecl;
        boolean isStatic = Cst.has(node, TokenType.STATICTK);
        Type type = Cst.build(node, CstType.BType, n -> (Type)n.accept(this));
        CstType specType = isConst ? CstType.ConstDef : CstType.VarDef;
        List<VarSpec> varSpecs = Cst.buildAll(node, specType, n -> (VarSpec)n.accept(this));
        VarDecl varDecl = new VarDecl(isConst, isStatic, type, varSpecs);
        Cst.findFirstToken(node).ifPresent(token -> varDecl.lineNumber = token.line());
        return varDecl;
    }

    private VarSpec buildVarSpec(NonTerm node) {
        String name = Cst.lexeme(node, TokenType.IDENFR);
        List<Expr> dims = Cst.buildAll(node, CstType.ConstExp, n -> (Expr)n.accept(this));
        Expr initVal = Cst.buildOpt(node, CstType.ConstInitVal, n -> (Expr)n.accept(this))
            .or(() -> Cst.buildOpt(node, CstType.InitVal, n -> (Expr)n.accept(this))).orElse(null);
        VarSpec varSpec = new VarSpec(name, dims, initVal);
        Cst.find(node, TokenType.IDENFR).ifPresent(token -> varSpec.lineNumber = token.line());
        return varSpec;
    }

    private Expr buildInitVal(NonTerm node) {
        if (Cst.has(node, TokenType.LBRACE)) {
            CstType itemType = node.type() == CstType.ConstInitVal ? CstType.ConstExp : CstType.Exp;
            List<Expr> vals = Cst.buildAll(node, itemType, n -> (Expr)n.accept(this));
            ArrayInitializer arrayInitializer = new ArrayInitializer(vals);
            Cst.find(node, TokenType.LBRACE).ifPresent(token -> arrayInitializer.lineNumber = token.line());
            return arrayInitializer;
        } else {
            return (Expr)node.children().get(0).accept(this);
        }
    }

    private BlockStmt buildBlockStmt(NonTerm node) {
        List<AstNode> items = Cst.buildAll(node, CstType.BlockItem, n -> (AstNode)n.accept(this));
        BlockStmt blockStmt = new BlockStmt(items);
        Cst.find(node, TokenType.LBRACE).ifPresent(token -> blockStmt.lineNumber = token.line());
        Cst.find(node, TokenType.RBRACE).ifPresent(token -> blockStmt.endLineNumber = token.line());
        return blockStmt;
    }

    private AssignStmt buildAssignStmt(NonTerm node) {
        LVal lVal = Cst.build(node, CstType.LVal, n -> (LVal)n.accept(this));
        Expr rVal = Cst.build(node, CstType.Exp, n -> (Expr)n.accept(this));
        AssignStmt assignStmt = new AssignStmt(lVal, rVal);
        assignStmt.lineNumber = lVal.lineNumber;
        return assignStmt;
    }

    private IfStmt buildIfStmt(NonTerm node) {
        Expr cond = Cst.build(node, CstType.Cond, n -> (Expr)n.accept(this));
        Stmt then = Cst.buildNth(node, CstType.Stmt, 0, n -> (Stmt)n.accept(this));
        Stmt elseStmt = Cst.buildNth(node, CstType.Stmt, 1, n -> (Stmt)n.accept(this));
        IfStmt ifStmt = new IfStmt(cond, then, elseStmt);
        Cst.find(node, TokenType.IFTK).ifPresent(token -> ifStmt.lineNumber = token.line());
        return ifStmt;
    }

    @SuppressWarnings("unchecked")
    private Stmt buildForLoopStmt(NonTerm node) {
        List<AssignStmt> init =
            Cst.buildNthOpt(node, CstType.ForStmt, 0, n -> (List<AssignStmt>)n.accept(this)).orElse(null);
        Expr cond = Cst.build(node, CstType.Cond, n -> (Expr)n.accept(this));
        List<AssignStmt> post =
            Cst.buildNthOpt(node, CstType.ForStmt, 1, n -> (List<AssignStmt>)n.accept(this)).orElse(null);
        Stmt body = Cst.build(node, CstType.Stmt, n -> (Stmt)n.accept(this));
        ForLoopStmt forLoopStmt = new ForLoopStmt(init, cond, post, body);
        Cst.find(node, TokenType.FORTK).ifPresent(token -> forLoopStmt.lineNumber = token.line());
        return forLoopStmt;
    }

    private List<AssignStmt> buildForStmt(NonTerm node) {
        List<AssignStmt> assignStmts = new ArrayList<>();
        List<CstNode> children = node.children();
        for (int i = 0; i < children.size(); i += 4) {
            LVal lVal = (LVal)children.get(i).accept(this);
            Expr rVal = (Expr)children.get(i + 2).accept(this);
            AssignStmt assignStmt = new AssignStmt(lVal, rVal);
            assignStmt.lineNumber = lVal.lineNumber;
            assignStmts.add(assignStmt);
        }
        return assignStmts;
    }

    private ReturnStmt buildReturnStmt(NonTerm node) {
        Expr retVal = Cst.build(node, CstType.Exp, n -> (Expr)n.accept(this));
        ReturnStmt returnStmt = new ReturnStmt(retVal);
        Cst.find(node, TokenType.RETURNTK).ifPresent(token -> returnStmt.lineNumber = token.line());
        return returnStmt;
    }

    private PrintfStmt buildPrintfStmt(NonTerm node) {
        String formatStr = Cst.lexeme(node, TokenType.STRCON);
        List<Expr> args = Cst.buildAll(node, CstType.Exp, n -> (Expr)n.accept(this));
        PrintfStmt printfStmt = new PrintfStmt(formatStr, args);
        Cst.find(node, TokenType.PRINTFTK).ifPresent(token -> printfStmt.lineNumber = token.line());
        return printfStmt;
    }

    private ExprStmt buildExprStmt(NonTerm node) {
        Expr expr = Cst.build(node, CstType.Exp, n -> (Expr)n.accept(this));
        ExprStmt exprStmt = new ExprStmt(expr);
        if (expr != null) {
            exprStmt.lineNumber = expr.lineNumber;
        } else {
            Cst.find(node, TokenType.SEMICN).ifPresent(token -> exprStmt.lineNumber = token.line());
        }
        return exprStmt;
    }

    private Expr buildBinaryExpr(NonTerm node) {
        List<CstNode> children = node.children();
        Expr left = (Expr)children.get(0).accept(this);
        for (int i = 1; i < children.size(); i += 2) {
            Token opt = (Token)Cst.peel(children.get(i));
            TokenType op = opt.type();
            Expr right = (Expr)children.get(i + 1).accept(this);
            BinaryExpr binaryExpr = new BinaryExpr(left, op, right);
            binaryExpr.lineNumber = opt.line();
            left = binaryExpr;
        }
        return left;
    }

    private Expr buildUnaryExpr(NonTerm node) {
        if (Cst.has(node, CstType.UnaryOp)) {
            Token opt = (Token)Cst.build(node, CstType.UnaryOp, Cst::peel);
            Expr operand = Cst.build(node, CstType.UnaryExp, n -> (Expr)n.accept(this));
            UnaryExpr unaryExpr = new UnaryExpr(opt.type(), operand);
            unaryExpr.lineNumber = opt.line();
            return unaryExpr;
        }
        return (Expr)node.children().get(0).accept(this);
    }

    private Expr buildPrimaryExpr(NonTerm node) {
        if (Cst.has(node, TokenType.LPARENT)) {
            return Cst.build(node, CstType.Exp, n -> (Expr)n.accept(this));
        } else {
            return (Expr)node.children().get(0).accept(this);
        }
    }

    private LVal buildLVal(NonTerm node) {
        String name = Cst.lexeme(node, TokenType.IDENFR);
        List<Expr> indices = Cst.buildAll(node, CstType.Exp, n -> (Expr)n.accept(this));
        LVal lVal = new LVal(name, indices);
        Cst.find(node, TokenType.IDENFR).ifPresent(token -> lVal.lineNumber = token.line());
        return lVal;
    }

    private IntLiteral buildNumber(NonTerm node) {
        String name = Cst.lexeme(node, TokenType.INTCON);
        IntLiteral intLiteral = new IntLiteral(Integer.parseInt(name));
        Cst.find(node, TokenType.INTCON).ifPresent(token -> intLiteral.lineNumber = token.line());
        return intLiteral;
    }

    @SuppressWarnings("unchecked")
    private FuncCall buildFuncCall(NonTerm node) {
        String name = Cst.lexeme(node, TokenType.IDENFR);
        List<Expr> args =
            Cst.buildOpt(node, CstType.FuncRParams, n -> (List<Expr>)n.accept(this)).orElse(Collections.emptyList());
        FuncCall funcCall = new FuncCall(name, args);
        Cst.find(node, TokenType.IDENFR).ifPresent(token -> funcCall.lineNumber = token.line());
        return funcCall;
    }

    private List<Expr> buildFuncRParams(NonTerm node) {
        return Cst.buildAll(node, CstType.Exp, n -> (Expr)n.accept(this));
    }

    private static final class Cst {

        public static Optional<Token> findFirstToken(CstNode node) {
            if (node instanceof Token t) {
                return Optional.of(t);
            }
            if (node instanceof NonTerm nt) {
                for (CstNode child : nt.children()) {
                    Optional<Token> token = findFirstToken(child);
                    if (token.isPresent()) {
                        return token;
                    }
                }
            }
            return Optional.empty();
        }

        public static Optional<NonTerm> find(NonTerm node, CstType type) {
            return node.children().stream().filter(n -> n instanceof NonTerm nt && nt.type() == type)
                .map(n -> (NonTerm)n).findFirst();
        }

        public static Optional<NonTerm> findNth(NonTerm node, CstType type, int nth) {
            return node.children().stream().filter(n -> n instanceof NonTerm nt && nt.type() == type)
                .map(n -> (NonTerm)n).skip(nth).findFirst();
        }

        public static List<NonTerm> findAll(NonTerm node, CstType type) {
            return node.children().stream().filter(n -> n instanceof NonTerm nt && nt.type() == type)
                .map(n -> (NonTerm)n).toList();
        }

        public static Optional<Token> find(NonTerm node, TokenType type) {
            return node.children().stream().map(Cst::peel).filter(n -> n instanceof Token t && t.type() == type)
                .map(n -> (Token)n).findFirst();
        }

        public static boolean has(NonTerm node, TokenType type) {
            return find(node, type).isPresent();
        }

        public static boolean has(NonTerm node, CstType type) {
            return find(node, type).isPresent();
        }

        public static String lexeme(NonTerm node, TokenType type) {
            return find(node, type).map(Token::lexeme).orElse(null);
        }

        public static <T> T build(NonTerm node, CstType type, Function<CstNode, T> v) {
            return find(node, type).map(v).orElse(null);
        }

        public static <T> T buildNth(NonTerm node, CstType type, int n, Function<CstNode, T> visitor) {
            return findNth(node, type, n).map(visitor).orElse(null);
        }

        public static <T> Optional<T> buildOpt(NonTerm node, CstType type, Function<CstNode, T> v) {
            return find(node, type).map(v);
        }

        public static <T> Optional<T> buildNthOpt(NonTerm node, CstType type, int n, Function<CstNode, T> visitor) {
            return findNth(node, type, n).map(visitor);
        }

        public static <T> List<T> buildAll(NonTerm node, CstType type, Function<CstNode, T> v) {
            return findAll(node, type).stream().map(v).toList();
        }

        public static CstNode peel(CstNode node) {
            // Cannot be used in Left Recursion Rules
            while (node instanceof NonTerm nt && nt.children().size() == 1) {
                node = nt.children().get(0);
            }
            return node;
        }
    }
}
