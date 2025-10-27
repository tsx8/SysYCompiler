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
import top.tsxb.compiler.frontend.cst.TokenStream;
import top.tsxb.compiler.frontend.cst.TokenType;
import top.tsxb.compiler.utils.BacktrackMgr;
import top.tsxb.compiler.utils.Backtrackable;

/** The type Parser. */
@SuppressWarnings({"checkstyle:AbbreviationAsWordInName", "checkstyle:MethodName"})
public class Parser {

  @FunctionalInterface
  interface ParserCombinator {
    Optional<List<CstNode>> parse(Parser parser);
  }

  private final TokenStream tokens;
  private final ErrorReporter reporter;
  private final BacktrackMgr backtrackMgr = new BacktrackMgr();
  private final List<String> logs = new ArrayList<>();

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
    backtrackMgr.register(
        new Backtrackable<Integer>() {
          @Override
          public Integer save() {
            return logs.size();
          }

          @Override
          public void restore(Integer pos) {
            if (pos >= 0 && pos <= logs.size()) {
              while (logs.size() > pos) {
                logs.remove(logs.size() - 1);
              }
            }
          }
        });
    initializeRules();
  }

  /** Parse. */
  public CstNode parse() {
    Optional<List<CstNode>> result = rule(CompUnit).parse(this);

    if (CompilerConfig.DEBUG) {
      for (String log : logs) {
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
    define(CompUnit, seq(many(rule(Decl)), many(rule(FuncDef)), rule(MainFuncDef)));
    // 声明 Decl → ConstDecl | VarDecl
    define(Decl, or(rule(ConstDecl), rule(VarDecl)));
    // 常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // i
    define(
        ConstDecl,
        seq(
            term(CONSTTK),
            rule(BType),
            rule(ConstDef),
            many(seq(term(COMMA), rule(ConstDef))),
            term(SEMICN, "i")));
    // 基本类型 BType → 'int'
    define(BType, term(INTTK));
    // 常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // k
    define(
        ConstDef,
        seq(
            term(IDENFR),
            opt(seq(term(LBRACK), rule(ConstExp), term(RBRACK, "k"))),
            term(ASSIGN),
            rule(ConstInitVal)));
    // 常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
    define(
        ConstInitVal,
        or(
            rule(ConstExp),
            seq(
                term(LBRACE),
                opt(seq(rule(ConstExp), many(seq(term(COMMA), rule(ConstExp))))),
                term(RBRACE, "k"))));
    // 变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // i
    define(
        VarDecl,
        seq(
            opt(term(STATICTK)),
            rule(BType),
            rule(VarDef),
            many(seq(term(COMMA), rule(VarDef))),
            term(SEMICN, "i")));
    // 变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // k
    define(
        VarDef,
        or(
            seq(
                term(IDENFR),
                opt(seq(term(LBRACK), rule(ConstExp), term(RBRACK, "k"))),
                term(ASSIGN),
                rule(InitVal)),
            seq(term(IDENFR), opt(seq(term(LBRACK), rule(ConstExp), term(RBRACK, "k"))))));
    // 变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
    define(
        InitVal,
        or(
            rule(Exp),
            seq(
                term(LBRACE),
                opt(seq(rule(Exp), many(seq(term(COMMA), rule(Exp))))),
                term(RBRACE))));
    // 函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // j
    define(
        FuncDef,
        seq(
            rule(FuncType),
            term(IDENFR),
            term(LPARENT),
            opt(rule(FuncFParams)),
            term(RPARENT, "j"),
            rule(Block)));
    // 主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // j
    define(
        MainFuncDef,
        seq(term(INTTK), term(MAINTK), term(LPARENT), term(RPARENT, "j"), rule(Block)));
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
    // | [Exp] ';' // i
    // | Block
    // | 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // j
    // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
    // | 'break' ';' | 'continue' ';' // i
    // | 'return' [Exp] ';' // i
    // | 'printf' '(' StringConst { ',' Exp } ')' ';' // i j
    define(
        Stmt,
        or(
            seq(rule(LVal), term(ASSIGN), rule(Exp), term(SEMICN, "i")),
            seq(opt(rule(Exp)), term(SEMICN, "i")),
            rule(Block),
            seq(
                term(IFTK),
                term(LPARENT),
                rule(Cond),
                term(RPARENT, "j"),
                rule(Stmt),
                opt(seq(term(ELSETK), rule(Stmt)))),
            seq(
                term(FORTK),
                term(LPARENT),
                opt(rule(ForStmt)),
                term(SEMICN),
                opt(rule(Cond)),
                term(SEMICN),
                opt(rule(ForStmt)),
                term(RPARENT),
                rule(Stmt)),
            or(seq(term(BREAKTK), term(SEMICN, "i")), seq(term(CONTINUETK), term(SEMICN, "i"))),
            seq(term(RETURNTK), opt(rule(Exp)), term(SEMICN, "i")),
            seq(
                term(PRINTFTK),
                term(LPARENT),
                term(STRCON),
                many(seq(term(COMMA), rule(Exp))),
                term(RPARENT, "j"),
                term(SEMICN, "i"))));
    // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
    define(
        ForStmt,
        seq(
            rule(LVal),
            term(ASSIGN),
            rule(Exp),
            many(seq(term(COMMA), rule(LVal), term(ASSIGN), rule(Exp)))));
    // 表达式 Exp → AddExp
    define(Exp, rule(AddExp));
    // 条件表达式 Cond → LOrExp
    define(Cond, rule(LOrExp));
    // 左值表达式 LVal → Ident ['[' Exp ']'] // k
    define(LVal, seq(term(IDENFR), opt(seq(term(LBRACK), rule(Exp), term(RBRACK, "k")))));
    // 基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number // j
    define(
        PrimaryExp,
        or(seq(term(LPARENT), rule(Exp), term(RPARENT, "j")), rule(LVal), rule(Number)));
    // 数值 Number → IntConst
    define(Number, term(INTCON));
    // 一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp // j
    define(
        UnaryExp,
        or(
            seq(term(IDENFR), term(LPARENT), opt(rule(FuncRParams)), term(RPARENT, "j")),
            seq(rule(UnaryOp), rule(UnaryExp)),
            rule(PrimaryExp)));
    // 单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
    define(UnaryOp, or(term(PLUS), term(MINU), term(NOT)));
    // 函数实参表 FuncRParams → Exp { ',' Exp }
    define(FuncRParams, seq(rule(Exp), many(seq(term(COMMA), rule(Exp)))));
    // 乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
    // 消除左递归：MulExp -> UnaryExp { ('*' | '/' | '%') UnaryExp }
    define(
        MulExp,
        seq(rule(UnaryExp), many(seq(or(term(MULT), term(DIV), term(MOD)), rule(UnaryExp)))));
    // 加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
    // 消除左递归：AddExp -> MulExp { ('+' | '−') MulExp }
    define(AddExp, seq(rule(MulExp), many(seq(or(term(PLUS), term(MINU)), rule(MulExp)))));
    // 关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
    // 消除左递归：RelExp -> AddExp { ('<' | '>' | '<=' | '>=') AddExp }
    define(
        RelExp,
        seq(rule(AddExp), many(seq(or(term(LSS), term(GRE), term(LEQ), term(GEQ)), rule(AddExp)))));
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
          reporter.report(tokens.peek(-1).line(), ErrorType.fromCode(errorCode), type.name());
        } catch (IllegalArgumentException e) {
          throw new SyntacticException("Unknown error code: " + errorCode);
        }
      }
      return Optional.empty();
    };
  }

  private ParserCombinator term(TokenType type) {
    return term(type, null);
  }

  private void log(String msg) {
    logs.add(msg);
  }

  private ParserCombinator nonTerm(CstType type, ParserCombinator rule) {
    return parser -> {
      log("-> Enter " + type);
      Optional<List<CstNode>> res = rule.parse(parser);
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
      int backup = parser.tokens.save();
      List<CstNode> results = new ArrayList<>();
      for (ParserCombinator combinator : combinators) {
        Optional<List<CstNode>> result = combinator.parse(parser);
        if (result.isEmpty()) {
          parser.tokens.restore(backup);
          return Optional.empty();
        }
        results.addAll(result.get());
      }
      return Optional.of(results);
    };
  }

  private ParserCombinator or(ParserCombinator... combinators) {
    return parser -> {
      BacktrackMgr.Snapshot snapshot = backtrackMgr.save();

      for (ParserCombinator combinator : combinators) {
        Optional<List<CstNode>> result = combinator.parse(parser);
        if (result.isPresent()) {
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
        Optional<List<CstNode>> result = combinator.parse(parser);
        if (result.isEmpty()) {
          break;
        }
        results.addAll(result.get());
      }
      return Optional.of(results);
    };
  }

  private ParserCombinator opt(ParserCombinator combinator) {
    return parser -> {
      Optional<List<CstNode>> result = combinator.parse(parser);
      return result.or(() -> Optional.of(new ArrayList<>()));
    };
  }
}
