package top.tsxb.compiler.frontend.parser;

import static top.tsxb.compiler.frontend.cst.CstType.*;
import static top.tsxb.compiler.frontend.cst.TokenType.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;
import top.tsxb.compiler.config.CompilerConfig;
import top.tsxb.compiler.frontend.cst.CstNode;
import top.tsxb.compiler.frontend.cst.CstType;
import top.tsxb.compiler.frontend.cst.NonTerm;
import top.tsxb.compiler.frontend.cst.Token;
import top.tsxb.compiler.frontend.cst.TokenStream;
import top.tsxb.compiler.frontend.cst.TokenType;
import top.tsxb.compiler.utils.BacktrackMgr;

/**
 * The type Parser.
 */
public class Parser {

    private final TokenStream tokens;
    private final ErrorReporter reporter;
    private final BacktrackMgr backtrackMgr = new BacktrackMgr();
    private final DebugLogger logger = new DebugLogger();
    private final Map<CstType, ParserCombinator> rules = new EnumMap<>(CstType.class);

    /**
     * Instantiates a new Parser.
     *
     * @param tokens the tokens
     * @param reporter the reporter
     */
    public Parser(TokenStream tokens, ErrorReporter reporter) {
        this.tokens = tokens;
        this.reporter = reporter;
        backtrackMgr.register(this.tokens);
        backtrackMgr.register(this.reporter);
        initializeRules();
    }

    private static class DebugLogger {
        private final List<String> logs = new ArrayList<>();
        private int indentLevel = 0;

        private int save() {
            return logs.size();
        }

        private void restore(int pos) {
            if (pos >= 0 && pos <= logs.size()) {
                while (logs.size() > pos) {
                    logs.remove(logs.size() - 1);
                }
            }
        }

        private void log(String message) {
            logs.add("  ".repeat(indentLevel) + message);
        }

        private void indent() {
            indentLevel++;
        }

        private void dedent() {
            indentLevel--;
        }
    }

    /**
     * Parse.
     */
    public CstNode parse() {
        Optional<List<CstNode>> result = rule(CompUnit).parse(this);

        if (CompilerConfig.DEBUG) {
            for (String log : logger.logs) {
                System.out.println(log);
            }
        }

        if (result.isPresent() && !result.get().isEmpty()) {
            return result.get().get(0);
        }
        throw new SyntacticException("Failed to parse CompUnit.");
    }

    private void initializeRules() {
        // 编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef EOF
        // 修改为 CompUnit -> { Decl | FuncDef } MainFuncDef
        define(CompUnit, seq(many(or(rule(FuncDef), rule(Decl))), rule(MainFuncDef)));
        // 声明 Decl → ConstDecl | VarDecl
        define(Decl, or(rule(ConstDecl), rule(VarDecl)));
        // 常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // i
        define(ConstDecl,
            seq(term(CONSTTK), rule(BType), rule(ConstDef), many(seq(term(COMMA), rule(ConstDef))), term(SEMICN, "i")));
        // 基本类型 BType → 'int'
        define(BType, term(INTTK));
        // 常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // k
        define(ConstDef, seq(term(IDENFR), opt(seq(term(LBRACK), rule(ConstExp), term(RBRACK, "k"))), term(ASSIGN),
            rule(ConstInitVal)));
        // 常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
        define(ConstInitVal, or(rule(ConstExp),
            seq(term(LBRACE), opt(seq(rule(ConstExp), many(seq(term(COMMA), rule(ConstExp))))), term(RBRACE, "k"))));
        // 变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // i
        define(VarDecl, seq(opt(term(STATICTK)), rule(BType), rule(VarDef), many(seq(term(COMMA), rule(VarDef))),
            term(SEMICN, "i")));
        // 变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '='
        // InitVal // k
        define(VarDef, or(
            seq(term(IDENFR), opt(seq(term(LBRACK), rule(ConstExp), term(RBRACK, "k"))), term(ASSIGN), rule(InitVal)),
            seq(term(IDENFR), opt(seq(term(LBRACK), rule(ConstExp), term(RBRACK, "k"))))));
        // 变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
        define(InitVal,
            or(rule(Exp), seq(term(LBRACE), opt(seq(rule(Exp), many(seq(term(COMMA), rule(Exp))))), term(RBRACE))));
        // 函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // j
        define(FuncDef,
            seq(rule(FuncType), term(IDENFR), term(LPARENT), opt(rule(FuncFParams)), term(RPARENT, "j"), rule(Block)));
        // 主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // j
        define(MainFuncDef, seq(term(INTTK), term(MAINTK), term(LPARENT), term(RPARENT, "j"), rule(Block)));
        // 函数类型 FuncType → 'void' | 'int'
        define(FuncType, or(term(VOIDTK), term(INTTK)));
        // 函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
        define(FuncFParams, seq(rule(FuncFParam), many(seq(term(COMMA), rule(FuncFParam)))));
        // 函数形参 FuncFParam → BType Ident ['[' ']'] // k
        define(FuncFParam, seq(rule(BType), term(IDENFR), opt(seq(term(LBRACK), term(RBRACK, "k")))));
        // 语句块 Block → '{' { BlockItem } '}'
        define(Block, seq(term(LBRACE), many(rule(BlockItem)), term(RBRACE)));
        // 语句块项 BlockItem → Decl | Stmt
        define(BlockItem, or(rule(Decl), rule(Stmt)));
        // 语句 Stmt → LVal '=' Exp ';' // i
        // | Block
        // | 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // j
        // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
        // | 'break' ';' // i
        // | 'continue' ';' // i
        // | 'return' [Exp] ';' // i
        // | 'printf' '(' StringConst { ',' Exp } ')' ';' // i j
        // | [Exp] ';' // i
        define(Stmt, or(seq(rule(LVal), term(ASSIGN), rule(Exp), term(SEMICN, "i")), // AssignStmt
            rule(Block), // BlockStmt
            seq(term(IFTK), term(LPARENT), rule(Cond), term(RPARENT, "j"), rule(Stmt),
                opt(seq(term(ELSETK), rule(Stmt)))), // IfStmt
            seq(term(FORTK), term(LPARENT), opt(rule(ForStmt)), term(SEMICN), opt(rule(Cond)), term(SEMICN),
                opt(rule(ForStmt)), term(RPARENT), rule(Stmt)), // ForLoopStmt
            seq(term(BREAKTK), term(SEMICN, "i")), // BreakStmt
            seq(term(CONTINUETK), term(SEMICN, "i")), // ContinueStmt
            seq(term(RETURNTK), opt(rule(Exp)), term(SEMICN, "i")), // ReturnStmt
            seq(term(PRINTFTK), term(LPARENT), term(STRCON), many(seq(term(COMMA), rule(Exp))), term(RPARENT, "j"),
                term(SEMICN, "i")), // PrintfStmt
            or(term(SEMICN), seq(rule(Exp), term(SEMICN, "i"))))); // ExpStmt
        // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
        define(ForStmt,
            seq(rule(LVal), term(ASSIGN), rule(Exp), many(seq(term(COMMA), rule(LVal), term(ASSIGN), rule(Exp)))));
        // 表达式 Exp → AddExp
        define(Exp, rule(AddExp));
        // 条件表达式 Cond → LOrExp
        define(Cond, rule(LOrExp));
        // 左值表达式 LVal → Ident ['[' Exp ']'] // k
        define(LVal, seq(term(IDENFR), opt(seq(term(LBRACK), rule(Exp), term(RBRACK, "k")))));
        // 基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number // j
        define(PrimaryExp, or(seq(term(LPARENT), rule(Exp), term(RPARENT, "j")), rule(LVal), rule(Number)));
        // 数值 Number → IntConst
        define(Number, term(INTCON));
        // 一元表达式 UnaryExp → Ident '(' [FuncRParams] ')' | PrimaryExp | UnaryOp UnaryExp // j
        define(UnaryExp, or(seq(term(IDENFR), term(LPARENT), opt(rule(FuncRParams)), term(RPARENT, "j")),
            rule(PrimaryExp), seq(rule(UnaryOp), rule(UnaryExp))));
        // 单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
        define(UnaryOp, or(term(PLUS), term(MINU), term(NOT)));
        // 函数实参表 FuncRParams → Exp { ',' Exp }
        define(FuncRParams, seq(rule(Exp), many(seq(term(COMMA), rule(Exp)))));
        // 乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
        // 消除左递归：MulExp -> UnaryExp { ('*' | '/' | '%') UnaryExp }
        define(MulExp, seq(rule(UnaryExp), many(seq(or(term(MULT), term(DIV), term(MOD)), rule(UnaryExp)))));
        // 加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
        // 消除左递归：AddExp -> MulExp { ('+' | '−') MulExp }
        define(AddExp, seq(rule(MulExp), many(seq(or(term(PLUS), term(MINU)), rule(MulExp)))));
        // 关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
        // 消除左递归：RelExp -> AddExp { ('<' | '>' | '<=' | '>=') AddExp }
        define(RelExp, seq(rule(AddExp), many(seq(or(term(LSS), term(GRE), term(LEQ), term(GEQ)), rule(AddExp)))));
        // 相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
        // 消除左递归：EqExp -> RelExp { ('==' | '!=') RelExp }
        define(EqExp, seq(rule(RelExp), many(seq(or(term(EQL), term(NEQ)), rule(RelExp)))));
        // 逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
        // 消除左递归：LAndExp -> EqExp { '&&' EqExp }
        define(LAndExp, seq(rule(EqExp), many(seq(term(AND), rule(EqExp)))));
        // 逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
        // 消除左递归：LOrExp -> LAndExp { '||' LAndExp }
        define(LOrExp, seq(rule(LAndExp), many(seq(term(OR), rule(LAndExp)))));
        // 常量表达式 ConstExp → AddExp 注：使用的 Ident 必须是常量
        define(ConstExp, rule(AddExp));
    }

    private void define(CstType type, ParserCombinator logic) {
        rules.put(type, nonTerm(type, logic));
    }

    private ParserCombinator term(TokenType type, String errorCode) {
        return parser -> {
            if (tokens.peek(0).type() == type) {
                return Optional.of(List.of(tokens.advance()));
            }
            if (errorCode != null && !errorCode.isEmpty()) {
                try {
                    int line = tokens.peek(-1).line();
                    reporter.report(line, ErrorType.fromCode(errorCode), type.name());
                    String lexeme = switch (type) {
                        case SEMICN -> ";";
                        case RPARENT -> ")";
                        case RBRACK -> "]";
                        default -> "";
                    };
                    Token token = new Token(type, lexeme, line);
                    return Optional.of(List.of(token));
                } catch (IllegalArgumentException e) {
                    throw new SyntacticException("Unknown error code: " + errorCode);
                }
            }
            log("✗ Match failed for terminal: " + type + ". Found: " + tokens.peek(0).type());
            return Optional.empty();
        };
    }

    private ParserCombinator term(TokenType type) {
        return term(type, null);
    }

    private void log(String msg) {
        Token current = tokens.peek(0);
        String info = String.format("[line %d, pos %d, token: %s '%s']", current.line(), tokens.save(), current.type(),
            current.lexeme());
        logger.log(msg + " @ " + info);
    }

    private ParserCombinator nonTerm(CstType type, ParserCombinator rule) {
        return parser -> {
            log("-> Enter " + type);
            logger.indent();
            Optional<List<CstNode>> res = rule.parse(parser);
            logger.dedent();
            if (res.isPresent()) {
                log("<- Success " + type);
                NonTerm node = new NonTerm(type);
                res.get().forEach(node::add);
                return Optional.of(List.of(node));
            }
            log("<- Fail " + type);
            return Optional.empty();
        };
    }

    private ParserCombinator rule(CstType type) {
        return parser -> {
            ParserCombinator combinator = rules.get(type);
            if (combinator == null) {
                throw new SyntacticException("Unknown rule type in map: " + type);
            }
            return combinator.parse(parser);
        };
    }

    private ParserCombinator seq(ParserCombinator... combinators) {
        return parser -> {
            final int logs = logger.save();
            List<CstNode> results = new ArrayList<>();
            for (ParserCombinator combinator : combinators) {
                logger.indent();
                Optional<List<CstNode>> result = combinator.parse(parser);
                logger.dedent();
                if (result.isEmpty()) {
                    return Optional.empty();
                }
                results.addAll(result.get());
            }
            logger.restore(logs);
            return Optional.of(results);
        };
    }

    private ParserCombinator or(ParserCombinator... combinators) {
        return parser -> {
            final BacktrackMgr.Snapshot snapshot = backtrackMgr.save();
            final int logs = logger.save();

            for (ParserCombinator combinator : combinators) {
                logger.indent();
                Optional<List<CstNode>> result = combinator.parse(parser);
                logger.dedent();
                if (result.isPresent()) {
                    logger.restore(logs);
                    return result;
                }
                backtrackMgr.restore(snapshot);
            }
            return Optional.empty();
        };
    }

    private ParserCombinator many(ParserCombinator combinator) {
        return parser -> {
            List<CstNode> results = new ArrayList<>();
            while (true) {
                final BacktrackMgr.Snapshot snapshot = backtrackMgr.save();
                Optional<List<CstNode>> result = combinator.parse(parser);
                if (result.isEmpty()) {
                    backtrackMgr.restore(snapshot);
                    break;
                }
                results.addAll(result.get());
            }
            return Optional.of(results);
        };
    }

    private ParserCombinator opt(ParserCombinator combinator) {
        return parser -> {
            final BacktrackMgr.Snapshot snapshot = backtrackMgr.save();
            Optional<List<CstNode>> result = combinator.parse(parser);
            if (result.isEmpty()) {
                backtrackMgr.restore(snapshot);
                return Optional.of(new ArrayList<>());
            }
            return result;
        };
    }

    @FunctionalInterface
    interface ParserCombinator {
        Optional<List<CstNode>> parse(Parser parser);
    }
}
