package top.tsxb.compiler.frontend.parser;

import java.util.Arrays;
import java.util.Objects;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;
import top.tsxb.compiler.frontend.cst.CstNode;
import top.tsxb.compiler.frontend.cst.CstType;
import top.tsxb.compiler.frontend.cst.NonTerm;
import top.tsxb.compiler.frontend.cst.Token;
import top.tsxb.compiler.frontend.cst.TokenStream;
import top.tsxb.compiler.frontend.cst.TokenType;

/** The type Parser. */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public record Parser(TokenStream tokens, ErrorReporter reporter) {
  /** Parse. */
  public CstNode parse() {
    return parseCompUnit();
  }

  private boolean check(int offset, TokenType... types) {
    if (tokens.isAtEnd()) {
      return false;
    }
    for (TokenType type : types) {
      if (tokens.peek(offset).type() == type) {
        return true;
      }
    }
    return false;
  }

  private boolean check(TokenType... types) {
    return check(0, types);
  }

  private Token match(TokenType... types) {
    if (check(types)) {
      return tokens.advance();
    }
    return null;
  }

  private Token expect(String err, TokenType... types) {
    Token token = match(types);
    if (token != null) {
      return token;
    }
    if (Objects.equals(err, "")) {
      throw new SyntacticException(
          "Unexpected token: "
              + tokens.peek(0).type()
              + ", expected one of: "
              + Arrays.toString(types)
              + " at line "
              + tokens.peek(0).line());
    }
    try {
      reporter.report(tokens.peek(-1).line(), ErrorType.fromCode(err), types[0].name());
    } catch (IllegalArgumentException e) {
      throw new SyntacticException("Unknown error code: " + err);
    }
    return null;
  }

  private Token expect(TokenType... types) {
    return expect("", types);
  }

  // compUnit: decl* funcDef* mainFuncDef EOF;
  // FIRST SET: { CONSTTK, INTTK, STATICTK, VOIDTK }
  // 编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
  private CstNode parseCompUnit() {
    NonTerm node = new NonTerm(CstType.CompUnit);

    while (!check(1, TokenType.MAINTK)) {
      if (check(2, TokenType.LPARENT)) {
        break;
      }
      node.add(parseDecl());
    }
    while (!check(1, TokenType.MAINTK)) {
      node.add(parseFuncDef());
    }
    node.add(parseMainFuncDef());
    return node;
  }

  // decl: constDecl | varDecl;
  // FIRST SET: { CONSTTK, INTTK, STATICTK }
  // 声明 Decl → ConstDecl | VarDecl
  private CstNode parseDecl() {
    NonTerm node = new NonTerm(CstType.Decl);

    if (check(TokenType.CONSTTK)) {
      node.add(parseConstDecl());
    } else {
      node.add(parseVarDecl());
    }
    return node;
  }

  // constDecl: CONSTTK bType constDef (COMMA constDef)* SEMICN;
  // FIRST SET: { CONSTTK }
  // 常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // i
  private CstNode parseConstDecl() {
    NonTerm node = new NonTerm(CstType.ConstDecl);
    node.add(expect(TokenType.CONSTTK));
    node.add(parseBType());
    node.add(parseConstDef());
    while (check(TokenType.COMMA)) {
      node.add(match(TokenType.COMMA));
      node.add(parseConstDef());
    }
    node.add(expect("i", TokenType.SEMICN));
    return node;
  }

  // bType: INTTK;
  // FIRST SET: { INTTK }
  // 基本类型 BType → 'int'
  private CstNode parseBType() {
    NonTerm node = new NonTerm(CstType.BType);
    node.add(expect(TokenType.INTTK));
    return node;
  }

  // constDef: IDENFR (LBRACK constExp RBRACK)? ASSIGN constInitVal;
  // FIRST SET: { IDENFR }
  // 常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // k
  private CstNode parseConstDef() {
    NonTerm node = new NonTerm(CstType.ConstDef);
    node.add(expect(TokenType.IDENFR));
    if (check(TokenType.LBRACK)) { // only one dimension supported
      node.add(match(TokenType.LBRACK));
      node.add(parseConstExp());
      node.add(expect("k", TokenType.RBRACK));
    }
    node.add(expect(TokenType.ASSIGN));
    node.add(parseConstInitVal());
    return node;
  }

  // constInitVal: constExp | LBRACE (constExp (COMMA constExp)*)? RBRACE;
  // FIRST SET: { IDENFR, INTCON, LBRACE, LPARENT, MINU, NOT, PLUS }
  // 常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
  private CstNode parseConstInitVal() {
    NonTerm node = new NonTerm(CstType.ConstInitVal);
    if (check(TokenType.LBRACE)) {
      node.add(match(TokenType.LBRACE));
      if (checkCommonFirstSet()) {
        node.add(parseConstExp());
        while (check(TokenType.COMMA)) {
          node.add(match(TokenType.COMMA));
          node.add(parseConstExp());
        }
      }
      node.add(expect(TokenType.RBRACE));
    } else {
      node.add(parseConstExp());
    }
    return node;
  }

  // varDecl: STATICTK? bType varDef (COMMA varDef)* SEMICN;
  // FIRST SET: { INTTK, STATICTK }
  // 变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // i
  private CstNode parseVarDecl() {
    NonTerm node = new NonTerm(CstType.VarDecl);
    if (check(TokenType.STATICTK)) {
      node.add(match(TokenType.STATICTK));
    }
    node.add(parseBType());
    node.add(parseVarDef());
    while (check(TokenType.COMMA)) {
      node.add(match(TokenType.COMMA));
      node.add(parseVarDef());
    }
    node.add(expect("i", TokenType.SEMICN));
    return node;
  }

  // varDef: IDENFR (LBRACK constExp RBRACK)? (ASSIGN initVal)?;
  // FIRST SET: { IDENFR }
  // 变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // k
  private CstNode parseVarDef() {
    NonTerm node = new NonTerm(CstType.VarDef);
    node.add(expect(TokenType.IDENFR));
    if (check(TokenType.LBRACK)) { // only one dimension supported
      node.add(match(TokenType.LBRACK));
      node.add(parseConstExp());
      node.add(expect("k", TokenType.RBRACK));
    }
    if (check(TokenType.ASSIGN)) {
      node.add(match(TokenType.ASSIGN));
      node.add(parseInitVal());
    }
    return node;
  }

  // initVal: exp | LBRACE (exp (COMMA exp)*)? RBRACE;
  // FIRST SET: { IDENFR, INTCON, LBRACE, LPARENT, MINU, NOT, PLUS }
  // 变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
  private CstNode parseInitVal() {
    NonTerm node = new NonTerm(CstType.InitVal);
    if (check(TokenType.LBRACE)) {
      node.add(match(TokenType.LBRACE));
      if (checkCommonFirstSet()) {
        node.add(parseExp());
        while (check(TokenType.COMMA)) {
          node.add(match(TokenType.COMMA));
          node.add(parseExp());
        }
      }
      node.add(expect(TokenType.RBRACE));
    } else {
      node.add(parseExp());
    }
    return node;
  }

  // funcDef: funcType IDENFR LPARENT funcFParams? RPARENT block;
  // FIRST SET: { INTTK, VOIDTK }
  // 函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // j
  private CstNode parseFuncDef() {
    NonTerm node = new NonTerm(CstType.FuncDef);
    node.add(parseFuncType());
    node.add(expect(TokenType.IDENFR));
    node.add(expect(TokenType.LPARENT));
    if (check(TokenType.INTTK)) {
      node.add(parseFuncFParams());
    }
    node.add(expect("j", TokenType.RPARENT));
    node.add(parseBlock());
    return node;
  }

  // mainFuncDef: INTTK MAINTK LPARENT RPARENT block;
  // FIRST SET: { INTTK }
  // 主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // j
  private CstNode parseMainFuncDef() {
    NonTerm node = new NonTerm(CstType.MainFuncDef);
    node.add(expect(TokenType.INTTK));
    node.add(expect(TokenType.MAINTK));
    node.add(expect(TokenType.LPARENT));
    node.add(expect("j", TokenType.RPARENT));
    node.add(parseBlock());
    return node;
  }

  // funcType: VOIDTK | INTTK;
  // FIRST SET: { INTTK, VOIDTK }
  // 函数类型 FuncType → 'void' | 'int'
  private CstNode parseFuncType() {
    NonTerm node = new NonTerm(CstType.FuncType);
    node.add(expect(TokenType.VOIDTK, TokenType.INTTK));
    return node;
  }

  // funcFParams: funcFParam (COMMA funcFParam)*;
  // FIRST SET: { INTTK }
  // 函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
  private CstNode parseFuncFParams() {
    NonTerm node = new NonTerm(CstType.FuncFParams);
    node.add(parseFuncFParam());
    while (check(TokenType.COMMA)) {
      node.add(match(TokenType.COMMA));
      node.add(parseFuncFParam());
    }
    return node;
  }

  // funcFParam: bType IDENFR (LBRACK RBRACK)?;
  // FIRST SET: { INTTK }
  // 函数形参 FuncFParam → BType Ident ['[' ']'] // k
  private CstNode parseFuncFParam() {
    NonTerm node = new NonTerm(CstType.FuncFParam);
    node.add(parseBType());
    node.add(expect(TokenType.IDENFR));
    if (check(TokenType.LBRACK)) { // only one dimension supported
      node.add(match(TokenType.LBRACK));
      node.add(expect("k", TokenType.RBRACK));
    }
    return node;
  }

  // block: LBRACE blockItem* RBRACE;
  // FIRST SET: { LBRACE }
  // 语句块 Block → '{' { BlockItem } '}'
  private CstNode parseBlock() {
    NonTerm node = new NonTerm(CstType.Block);
    node.add(expect(TokenType.LBRACE));
    while (check(
        TokenType.BREAKTK,
        TokenType.CONSTTK,
        TokenType.CONTINUETK,
        TokenType.FORTK,
        TokenType.IDENFR,
        TokenType.IFTK,
        TokenType.INTCON,
        TokenType.INTTK,
        TokenType.LBRACE,
        TokenType.LPARENT,
        TokenType.MINU,
        TokenType.NOT,
        TokenType.PLUS,
        TokenType.PRINTFTK,
        TokenType.RETURNTK,
        TokenType.SEMICN,
        TokenType.STATICTK)) {
      node.add(parseBlockItem());
    }
    node.add(expect(TokenType.RBRACE));
    return node;
  }

  // blockItem: decl | stmt;
  // FIRST SET: { BREAKTK, CONSTTK, CONTINUETK, FORTK, IDENFR, IFTK, INTCON, INTTK,
  //              LBRACE, LPARENT, MINU, NOT, PLUS, PRINTFTK, RETURNTK, SEMICN, STATICTK }
  // 语句块项 BlockItem → Decl | Stmt
  private CstNode parseBlockItem() {
    NonTerm node = new NonTerm(CstType.BlockItem);
    if (check(TokenType.CONSTTK, TokenType.INTTK, TokenType.STATICTK)) {
      node.add(parseDecl());
    } else {
      node.add(parseStmt());
    }
    return node;
  }

  // stmt: lVal ASSIGN exp SEMICN // IDENFR
  //     | exp? SEMICN // IDENFR, INTCON, LPARENT, MINU, NOT, PLUS, SEMICN
  //     | block // LBRACE
  //     | IFTK LPARENT cond RPARENT stmt (ELSETK stmt)? // IFTK
  //     | FORTK LPARENT forStmt? SEMICN cond? SEMICN forStmt? RPARENT stmt // FORTK
  //     | BREAKTK SEMICN // BREAKTK
  //     | CONTINUETK SEMICN // CONTINUETK
  //     | RETURNTK exp? SEMICN // RETURNTK
  //     | PRINTFTK LPARENT STRCON (COMMA exp)* RPARENT SEMICN // PRINTFTK
  //     ;
  // FIRST SET: { BREAKTK, CONTINUETK, FORTK, IDENFR, IFTK, INTCON, LBRACE,
  //              LPARENT, MINU, NOT, PLUS, PRINTFTK, RETURNTK, SEMICN }
  // 语句 Stmt → LVal '=' Exp ';' // i
  // | [Exp] ';' // i
  // | Block
  // | 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // j
  // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
  // | 'break' ';' | 'continue' ';' // i
  // | 'return' [Exp] ';' // i
  // | 'printf''('StringConst {','Exp}')'';' // i j
  private CstNode parseStmt() {
    NonTerm node = new NonTerm(CstType.Stmt);
    if (check(TokenType.LBRACE)) { // block
      node.add(parseBlock());
    } else if (check(TokenType.IFTK)) { // if
      node.add(match(TokenType.IFTK));
      node.add(expect(TokenType.LPARENT));
      node.add(parseCond());
      node.add(expect("j", TokenType.RPARENT));
      node.add(parseStmt());
      if (check(TokenType.ELSETK)) {
        node.add(match(TokenType.ELSETK));
        node.add(parseStmt());
      }
    } else if (check(TokenType.FORTK)) { // for
      node.add(match(TokenType.FORTK));
      node.add(expect(TokenType.LPARENT));
      if (check(TokenType.IDENFR)) {
        node.add(parseForStmt());
      }
      node.add(expect(TokenType.SEMICN));
      if (checkCommonFirstSet()) {
        node.add(parseCond());
      }
      node.add(expect(TokenType.SEMICN));
      if (check(TokenType.IDENFR)) {
        node.add(parseForStmt());
      }
      node.add(expect(TokenType.RPARENT));
      node.add(parseStmt());
    } else if (check(TokenType.BREAKTK, TokenType.CONTINUETK)) { // break | continue
      node.add(match(TokenType.BREAKTK, TokenType.CONTINUETK));
      node.add(expect("i", TokenType.SEMICN));
    } else if (check(TokenType.RETURNTK)) { // return
      node.add(match(TokenType.RETURNTK));
      if (checkCommonFirstSet()) {
        node.add(parseExp());
      }
      node.add(expect("i", TokenType.SEMICN));
    } else if (check(TokenType.PRINTFTK)) { // printf
      node.add(match(TokenType.PRINTFTK));
      node.add(expect(TokenType.LPARENT));
      node.add(expect(TokenType.STRCON));
      while (check(TokenType.COMMA)) {
        node.add(match(TokenType.COMMA));
        node.add(parseExp());
      }
      node.add(expect("j", TokenType.RPARENT));
      node.add(expect("i", TokenType.SEMICN));
    } else if (isAssignmentStmt()) {
      node.add(parseLVal());
      node.add(expect(TokenType.ASSIGN));
      node.add(parseExp());
      node.add(expect("i", TokenType.SEMICN));
    } else {
      if (checkCommonFirstSet()) {
        node.add(parseExp());
      }
      node.add(expect("i", TokenType.SEMICN));
    }
    return node;
  }

  // forStmt: lVal ASSIGN exp (COMMA lVal ASSIGN exp)*;
  // FIRST SET: { IDENFR }
  // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
  private CstNode parseForStmt() {
    NonTerm node = new NonTerm(CstType.ForStmt);
    node.add(parseLVal());
    node.add(expect(TokenType.ASSIGN));
    node.add(parseExp());
    while (check(TokenType.COMMA)) {
      node.add(match(TokenType.COMMA));
      node.add(parseLVal());
      node.add(expect(TokenType.ASSIGN));
      node.add(parseExp());
    }
    return node;
  }

  // exp: addExp;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 表达式 Exp → AddExp
  private CstNode parseExp() {
    NonTerm node = new NonTerm(CstType.Exp);
    node.add(parseAddExp());
    return node;
  }

  // cond: lOrExp;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 条件表达式 Cond → LOrExp
  private CstNode parseCond() {
    NonTerm node = new NonTerm(CstType.Cond);
    node.add(parseLOrExp());
    return node;
  }

  // lVal: IDENFR (LBRACK exp RBRACK)?;
  // FIRST SET: { IDENFR }
  // 左值表达式 LVal → Ident ['[' Exp ']'] // k
  private CstNode parseLVal() {
    NonTerm node = new NonTerm(CstType.LVal);
    node.add(expect(TokenType.IDENFR));
    if (check(TokenType.LBRACK)) { // only one dimension supported
      node.add(match(TokenType.LBRACK));
      node.add(parseExp());
      node.add(expect("k", TokenType.RBRACK));
    }
    return node;
  }

  // primaryExp: LPARENT exp RPARENT | lVal | number;
  // FIRST SET: { IDENFR, INTCON, LPARENT }
  // 基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number // j
  private CstNode parsePrimaryExp() {
    NonTerm node = new NonTerm(CstType.PrimaryExp);
    if (check(TokenType.LPARENT)) {
      node.add(match(TokenType.LPARENT));
      node.add(parseExp());
      node.add(expect("j", TokenType.RPARENT));
    } else if (check(TokenType.IDENFR)) {
      node.add(parseLVal());
    } else {
      node.add(parseNumber());
    }
    return node;
  }

  // number: INTCON;
  // FIRST SET: { INTCON }
  // 数值 Number → IntConst
  private CstNode parseNumber() {
    NonTerm node = new NonTerm(CstType.Number);
    node.add(expect(TokenType.INTCON));
    return node;
  }

  // unaryExp: primaryExp // IDENFR, INTCON, LPARENT
  //         | IDENFR LPARENT funcRParams? RPARENT // IDENFR
  //         | unaryOp unaryExp; // MINU, NOT, PLUS
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp // j
  private CstNode parseUnaryExp() {
    NonTerm node = new NonTerm(CstType.UnaryExp);
    if (check(TokenType.IDENFR) && check(1, TokenType.LPARENT)) {
      node.add(expect(TokenType.IDENFR));
      node.add(expect(TokenType.LPARENT));
      if (checkCommonFirstSet()) {
        node.add(parseFuncRParams());
      }
      node.add(expect("j", TokenType.RPARENT));
    } else if (check(TokenType.PLUS, TokenType.MINU, TokenType.NOT)) {
      node.add(parseUnaryOp());
      node.add(parseUnaryExp());
    } else {
      node.add(parsePrimaryExp());
    }
    return node;
  }

  // unaryOp: PLUS | MINU | NOT;
  // FIRST SET: { MINU, NOT, PLUS }
  // 单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
  private CstNode parseUnaryOp() {
    NonTerm node = new NonTerm(CstType.UnaryOp);
    node.add(expect(TokenType.PLUS, TokenType.MINU, TokenType.NOT));
    return node;
  }

  // funcRParams: exp (COMMA exp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 函数实参表 FuncRParams → Exp { ',' Exp }
  private CstNode parseFuncRParams() {
    NonTerm node = new NonTerm(CstType.FuncRParams);
    node.add(parseExp());
    while (check(TokenType.COMMA)) {
      node.add(match(TokenType.COMMA));
      node.add(parseExp());
    }
    return node;
  }

  // mulExp: unaryExp ( (MULT | DIV | MOD) unaryExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
  private CstNode parseMulExp() {
    NonTerm node = new NonTerm(CstType.MulExp);
    node.add(parseUnaryExp());
    while (check(TokenType.MULT, TokenType.DIV, TokenType.MOD)) {
      NonTerm parent = new NonTerm(CstType.MulExp);
      parent.add(node);
      parent.add(match(TokenType.MULT, TokenType.DIV, TokenType.MOD));
      parent.add(parseUnaryExp());
      node = parent;
    }
    return node;
  }

  // addExp: mulExp ( (PLUS | MINU) mulExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
  private CstNode parseAddExp() {
    NonTerm node = new NonTerm(CstType.AddExp);
    node.add(parseMulExp());
    while (check(TokenType.PLUS, TokenType.MINU)) {
      NonTerm parent = new NonTerm(CstType.AddExp);
      parent.add(node);
      parent.add(match(TokenType.PLUS, TokenType.MINU));
      parent.add(parseMulExp());
      node = parent;
    }
    return node;
  }

  // relExp: addExp ( (LSS | GRE | LEQ | GEQ) addExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
  private CstNode parseRelExp() {
    NonTerm node = new NonTerm(CstType.RelExp);
    node.add(parseAddExp());
    while (check(TokenType.LSS, TokenType.GRE, TokenType.LEQ, TokenType.GEQ)) {
      NonTerm parent = new NonTerm(CstType.RelExp);
      parent.add(node);
      parent.add(match(TokenType.LSS, TokenType.GRE, TokenType.LEQ, TokenType.GEQ));
      parent.add(parseAddExp());
      node = parent;
    }
    return node;
  }

  // eqExp: relExp ( (EQL | NEQ) relExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
  private CstNode parseEqExp() {
    NonTerm node = new NonTerm(CstType.EqExp);
    node.add(parseRelExp());
    while (check(TokenType.EQL, TokenType.NEQ)) {
      NonTerm parent = new NonTerm(CstType.EqExp);
      parent.add(node);
      parent.add(match(TokenType.EQL, TokenType.NEQ));
      parent.add(parseRelExp());
      node = parent;
    }
    return node;
  }

  // lAndExp: eqExp (AND eqExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
  private CstNode parseLAndExp() {
    NonTerm node = new NonTerm(CstType.LAndExp);
    node.add(parseEqExp());
    while (check(TokenType.AND)) {
      NonTerm parent = new NonTerm(CstType.LAndExp);
      parent.add(node);
      parent.add(match(TokenType.AND));
      parent.add(parseEqExp());
      node = parent;
    }
    return node;
  }

  // lOrExp: lAndExp (OR lAndExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
  private CstNode parseLOrExp() {
    NonTerm node = new NonTerm(CstType.LOrExp);
    node.add(parseLAndExp());
    while (check(TokenType.OR)) {
      NonTerm parent = new NonTerm(CstType.LOrExp);
      parent.add(node);
      parent.add(match(TokenType.OR));
      parent.add(parseLAndExp());
      node = parent;
    }
    return node;
  }

  // constExp: addExp;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 常量表达式 ConstExp → AddExp 注：使用的 Ident 必须是常量
  private CstNode parseConstExp() {
    NonTerm node = new NonTerm(CstType.ConstExp);
    node.add(parseAddExp());
    return node;
  }

  private boolean checkCommonFirstSet() {
    return check(
    TokenType.IDENFR,
    TokenType.INTCON,
    TokenType.LPARENT,
    TokenType.MINU,
    TokenType.NOT,
    TokenType.PLUS);
  }

  private boolean isAssignmentStmt() {
    int offset = 0;
    if (!check(offset, TokenType.IDENFR)) {
      return false;
    }
    offset++;
    if (check(offset, TokenType.LBRACK)) {
      offset++;
      while (!check(offset, TokenType.RBRACK) && !check(offset, TokenType.EOF)) {
        if (check(offset, TokenType.SEMICN)) {
          return false;
        }
        offset++;
      }
      if (check(offset, TokenType.RBRACK)) {
        offset++;
      }
    }
    return check(offset, TokenType.ASSIGN);
  }
}
