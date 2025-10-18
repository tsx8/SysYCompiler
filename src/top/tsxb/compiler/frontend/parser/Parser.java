package top.tsxb.compiler.frontend.parser;

import java.util.Arrays;
import java.util.Objects;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;
import top.tsxb.compiler.frontend.lexer.Token;
import top.tsxb.compiler.frontend.lexer.TokenStream;
import top.tsxb.compiler.frontend.lexer.TokenType;

/** The type Parser. */
public record Parser(TokenStream tokens, ErrorReporter reporter, SyntaxWriter writer) {
  /** Parse. */
  public void parse() {
    parseCompUnit();
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

  private boolean match(TokenType... types) {
    if (check(0, types)) {
      Token token = tokens.advance();
      writer.writeToken(token);
      return true;
    }
    return false;
  }

  private void expect(String errorCode, TokenType... types) {
    if (check(0, types)) {
      Token token = tokens.advance();
      writer.writeToken(token);
      return;
    }
    if (Objects.equals(errorCode, "")) {
      throw new SyntacticException("Unexpected token: " + tokens.peek(0).type()
                                   + ", expected one of: " + Arrays.toString(types)
                                   + " at line " + tokens.peek(0).line());
    }
    try {
      for (TokenType type : types) {
        reporter.report(tokens.peek(-1).line(), ErrorType.fromCode(errorCode), type.name());
      }
    } catch (IllegalArgumentException e) {
      throw new SyntacticException("Unknown error code: " + errorCode);
    }
  }

  // compUnit: decl* funcDef* mainFuncDef EOF;
  // FIRST SET: { CONSTTK, INTTK, STATICTK, VOIDTK }
  // 编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
  private void parseCompUnit() {
    while (!check(1, TokenType.MAINTK)) {
      if (check(2, TokenType.LPARENT)) {
        break;
      }
      parseDecl();
    }
    while (!check(1, TokenType.MAINTK)) {
      parseFuncDef();
    }
    parseMainFuncDef();
    writer.writeNonTerminal("CompUnit");
  }

  // decl: constDecl | varDecl;
  // FIRST SET: { CONSTTK, INTTK, STATICTK }
  // 声明 Decl → ConstDecl | VarDecl
  private void parseDecl() {
    if (check(0, TokenType.CONSTTK)) {
      parseConstDecl();
    } else {
      parseVarDecl();
    }
    writer.writeNonTerminal("Decl");
  }

  // constDecl: CONSTTK bType constDef (COMMA constDef)* SEMICN;
  // FIRST SET: { CONSTTK }
  // 常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // i
  private void parseConstDecl() {
    expect("", TokenType.CONSTTK);
    parseBType();
    do {
      parseConstDef();
    } while (match(TokenType.COMMA));
    expect("i", TokenType.SEMICN);
    writer.writeNonTerminal("ConstDecl");
  }

  // bType: INTTK;
  // FIRST SET: { INTTK }
  // 基本类型 BType → 'int'
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseBType() {
    expect("", TokenType.INTTK);
    writer.writeNonTerminal("BType");
  }

  // constDef: IDENFR (LBRACK constExp RBRACK)? ASSIGN constInitVal;
  // FIRST SET: { IDENFR }
  // 常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // k
  private void parseConstDef() {
    expect("", TokenType.IDENFR);
    if (match(TokenType.LBRACK)) { // only one dimension supported
      parseConstExp();
      expect("k", TokenType.RBRACK);
    }
    expect("", TokenType.ASSIGN);
    parseConstInitVal();
    writer.writeNonTerminal("ConstDef");
  }

  // constInitVal: constExp | LBRACE (constExp (COMMA constExp)*)? RBRACE;
  // FIRST SET: { IDENFR, INTCON, LBRACE, LPARENT, MINU, NOT, PLUS }
  // 常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
  private void parseConstInitVal() {
    if (match(TokenType.LBRACE)) {
      if (check(0, TokenType.IDENFR, TokenType.INTCON, TokenType.LPARENT,
                TokenType.MINU, TokenType.NOT, TokenType.PLUS)) {
        do {
          parseConstExp();
        } while (match(TokenType.COMMA));
      }
      expect("", TokenType.RBRACE);
    } else {
      parseConstExp();
    }
    writer.writeNonTerminal("ConstInitVal");
  }

  // varDecl: STATICTK? bType varDef (COMMA varDef)* SEMICN;
  // FIRST SET: { INTTK, STATICTK }
  // 变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // i
  private void parseVarDecl() {
    match(TokenType.STATICTK);
    parseBType();
    do {
      parseVarDef();
    } while (match(TokenType.COMMA));
    expect("i", TokenType.SEMICN);
    writer.writeNonTerminal("VarDecl");
  }

  // varDef: IDENFR (LBRACK constExp RBRACK)? (ASSIGN initVal)?;
  // FIRST SET: { IDENFR }
  // 变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // k
  private void parseVarDef() {
    expect("", TokenType.IDENFR);
    if (match(TokenType.LBRACK)) { // only one dimension supported
      parseConstExp();
      expect("k", TokenType.RBRACK);
    }
    if (match(TokenType.ASSIGN)) {
      parseInitVal();
    }
    writer.writeNonTerminal("VarDef");
  }

  // initVal: exp | LBRACE (exp (COMMA exp)*)? RBRACE;
  // FIRST SET: { IDENFR, INTCON, LBRACE, LPARENT, MINU, NOT, PLUS }
  // 变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
  private void parseInitVal() {
    if (match(TokenType.LBRACE)) {
      if (check(0, TokenType.IDENFR, TokenType.INTCON, TokenType.LPARENT,
                TokenType.MINU, TokenType.NOT, TokenType.PLUS)) {
        do {
          parseExp();
        } while (match(TokenType.COMMA));
      }
      expect("", TokenType.RBRACE);
    } else {
      parseExp();
    }
    writer.writeNonTerminal("InitVal");
  }

  // funcDef: funcType IDENFR LPARENT funcFParams? RPARENT block;
  // FIRST SET: { INTTK, VOIDTK }
  // 函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // j
  private void parseFuncDef() {
    parseFuncType();
    expect("", TokenType.IDENFR);
    expect("", TokenType.LPARENT);
    if (check(0, TokenType.INTTK)) {
      parseFuncFParams();
    }
    expect("j", TokenType.RPARENT);
    parseBlock();
    writer.writeNonTerminal("FuncDef");
  }

  // mainFuncDef: INTTK MAINTK LPARENT RPARENT block;
  // FIRST SET: { INTTK }
  // 主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // j
  private void parseMainFuncDef() {
    expect("", TokenType.INTTK);
    expect("", TokenType.MAINTK);
    expect("", TokenType.LPARENT);
    expect("j", TokenType.RPARENT);
    parseBlock();
    writer.writeNonTerminal("MainFuncDef");
  }

  // funcType: VOIDTK | INTTK;
  // FIRST SET: { INTTK, VOIDTK }
  // 函数类型 FuncType → 'void' | 'int'
  private void parseFuncType() {
    expect("", TokenType.VOIDTK, TokenType.INTTK);
    writer.writeNonTerminal("FuncType");
  }

  // funcFParams: funcFParam (COMMA funcFParam)*;
  // FIRST SET: { INTTK }
  // 函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseFuncFParams() {
    do {
      parseFuncFParam();
    } while (match(TokenType.COMMA));
    writer.writeNonTerminal("FuncFParams");
  }

  // funcFParam: bType IDENFR (LBRACK RBRACK)?;
  // FIRST SET: { INTTK }
  // 函数形参 FuncFParam → BType Ident ['[' ']'] // k
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseFuncFParam() {
    parseBType();
    expect("", TokenType.IDENFR);
    if (match(TokenType.LBRACK)) { // only one dimension supported
      expect("k", TokenType.RBRACK);
    }
    writer.writeNonTerminal("FuncFParam");
  }

  // block: LBRACE blockItem* RBRACE;
  // FIRST SET: { LBRACE }
  // 语句块 Block → '{' { BlockItem } '}'
  private void parseBlock() {
    expect("", TokenType.LBRACE);
    while (check(0, TokenType.BREAKTK, TokenType.CONSTTK, TokenType.CONTINUETK, TokenType.FORTK,
                 TokenType.IDENFR, TokenType.IFTK, TokenType.INTCON, TokenType.INTTK,
                 TokenType.LBRACE, TokenType.LPARENT, TokenType.MINU, TokenType.NOT, TokenType.PLUS,
                 TokenType.PRINTFTK, TokenType.RETURNTK, TokenType.SEMICN, TokenType.STATICTK)) {
      parseBlockItem();
    }
    expect("", TokenType.RBRACE);
    writer.writeNonTerminal("Block");
  }

  // blockItem: decl | stmt;
  // FIRST SET: { BREAKTK, CONSTTK, CONTINUETK, FORTK, IDENFR, IFTK, INTCON, INTTK,
  //              LBRACE, LPARENT, MINU, NOT, PLUS, PRINTFTK, RETURNTK, SEMICN, STATICTK }
  // 语句块项 BlockItem → Decl | Stmt
  private void parseBlockItem() {
    if (check(0, TokenType.CONSTTK, TokenType.INTTK, TokenType.STATICTK)) {
      parseDecl();
    } else {
      parseStmt();
    }
    writer.writeNonTerminal("BlockItem");
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
  private void parseStmt() {
    if (check(0, TokenType.LBRACE)) { // block
      parseBlock();
    } else if (match(TokenType.IFTK)) { // if
      expect("", TokenType.LPARENT);
      parseCond();
      expect("j", TokenType.RPARENT);
      parseStmt();
      if (match(TokenType.ELSETK)) {
        parseStmt();
      }
    } else if (match(TokenType.FORTK)) { // for
      expect("", TokenType.LPARENT);
      if (check(0, TokenType.IDENFR)) {
        parseForStmt();
      }
      expect("", TokenType.SEMICN);
      if (check(0, TokenType.IDENFR, TokenType.INTCON, TokenType.LPARENT,
                TokenType.MINU, TokenType.NOT, TokenType.PLUS)) {
        parseCond();
      }
      expect("", TokenType.SEMICN);
      if (check(0, TokenType.IDENFR)) {
        parseForStmt();
      }
      expect("", TokenType.RPARENT);
      parseStmt();
    } else if (match(TokenType.BREAKTK, TokenType.CONTINUETK)) { // break | continue
      expect("i", TokenType.SEMICN);
    } else if (match(TokenType.RETURNTK)) { // return
      if (check(0, TokenType.IDENFR, TokenType.INTCON, TokenType.LPARENT,
                TokenType.MINU, TokenType.NOT, TokenType.PLUS)) {
        parseExp();
      }
      expect("i", TokenType.SEMICN);
    } else if (match(TokenType.PRINTFTK)) { // printf
      expect("", TokenType.LPARENT);
      expect("", TokenType.STRCON);
      while (match(TokenType.COMMA)) {
        parseExp();
      }
      expect("j", TokenType.RPARENT);
      expect("i", TokenType.SEMICN);
    } else if (isAssignmentStmt()) {
      parseLVal();
      expect("", TokenType.ASSIGN);
      parseExp();
      expect("i", TokenType.SEMICN);
    } else {
      if (check(0, TokenType.IDENFR, TokenType.INTCON, TokenType.LPARENT,
                TokenType.MINU, TokenType.NOT, TokenType.PLUS)) {
        parseExp();
      }
      expect("i", TokenType.SEMICN);
    }
    writer.writeNonTerminal("Stmt");
  }

  // forStmt: lVal ASSIGN exp (COMMA lVal ASSIGN exp)*;
  // FIRST SET: { IDENFR }
  // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
  private void parseForStmt() {
    do {
      parseLVal();
      expect("", TokenType.ASSIGN);
      parseExp();
    } while (match(TokenType.COMMA));
    writer.writeNonTerminal("ForStmt");
  }

  // exp: addExp;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 表达式 Exp → AddExp
  private void parseExp() {
    parseAddExp();
    writer.writeNonTerminal("Exp");
  }

  // cond: lOrExp;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 条件表达式 Cond → LOrExp
  private void parseCond() {
    parseLOrExp();
    writer.writeNonTerminal("Cond");
  }

  // lVal: IDENFR (LBRACK exp RBRACK)?;
  // FIRST SET: { IDENFR }
  // 左值表达式 LVal → Ident ['[' Exp ']'] // k
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseLVal() {
    expect("", TokenType.IDENFR);
    if (match(TokenType.LBRACK)) { // only one dimension supported
      parseExp();
      expect("k", TokenType.RBRACK);
    }
    writer.writeNonTerminal("LVal");
  }

  // primaryExp: LPARENT exp RPARENT | lVal | number;
  // FIRST SET: { IDENFR, INTCON, LPARENT }
  // 基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number // j
  private void parsePrimaryExp() {
    if (match(TokenType.LPARENT)) {
      parseExp();
      expect("j", TokenType.RPARENT);
    } else if (check(0, TokenType.IDENFR)) {
      parseLVal();
    } else {
      parseNumber();
    }
    writer.writeNonTerminal("PrimaryExp");
  }

  // number: INTCON;
  // FIRST SET: { INTCON }
  // 数值 Number → IntConst
  private void parseNumber() {
    expect("", TokenType.INTCON);
    writer.writeNonTerminal("Number");
  }

  // unaryExp: primaryExp // IDENFR, INTCON, LPARENT
  //         | IDENFR LPARENT funcRParams? RPARENT // IDENFR
  //         | unaryOp unaryExp; // MINU, NOT, PLUS
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp // j
  private void parseUnaryExp() {
    if (check(0, TokenType.IDENFR) && check(1, TokenType.LPARENT)) {
      expect("", TokenType.IDENFR);
      expect("", TokenType.LPARENT);
      if (check(0, TokenType.IDENFR, TokenType.INTCON, TokenType.LPARENT,
                TokenType.MINU, TokenType.NOT, TokenType.PLUS)) {
        parseFuncRParams();
      }
      expect("j", TokenType.RPARENT);
    } else if (check(0, TokenType.PLUS, TokenType.MINU, TokenType.NOT)) {
      parseUnaryOp();
      parseUnaryExp();
    } else {
      parsePrimaryExp();
    }
    writer.writeNonTerminal("UnaryExp");
  }

  // unaryOp: PLUS | MINU | NOT;
  // FIRST SET: { MINU, NOT, PLUS }
  // 单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
  private void parseUnaryOp() {
    expect("", TokenType.PLUS, TokenType.MINU, TokenType.NOT);
    writer.writeNonTerminal("UnaryOp");
  }

  // funcRParams: exp (COMMA exp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 函数实参表 FuncRParams → Exp { ',' Exp }
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseFuncRParams() {
    do {
      parseExp();
    } while (match(TokenType.COMMA));
    writer.writeNonTerminal("FuncRParams");
  }

  // mulExp: unaryExp ( (MULT | DIV | MOD) unaryExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
  private void parseMulExp() {
    do {
      parseUnaryExp();
      writer.writeNonTerminal("MulExp");
    } while (match(TokenType.MULT, TokenType.DIV, TokenType.MOD));
  }

  // addExp: mulExp ( (PLUS | MINU) mulExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
  private void parseAddExp() {
    do {
      parseMulExp();
      writer.writeNonTerminal("AddExp");
    } while (match(TokenType.PLUS, TokenType.MINU));
  }

  // relExp: addExp ( (LSS | GRE | LEQ | GEQ) addExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
  private void parseRelExp() {
    do {
      parseAddExp();
      writer.writeNonTerminal("RelExp");
    } while (match(TokenType.LSS, TokenType.GRE, TokenType.LEQ, TokenType.GEQ));
  }

  // eqExp: relExp ( (EQL | NEQ) relExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
  private void parseEqExp() {
    do {
      parseRelExp();
      writer.writeNonTerminal("EqExp");
    } while (match(TokenType.EQL, TokenType.NEQ));
  }

  // lAndExp: eqExp (AND eqExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseLAndExp() {
    do {
      parseEqExp();
      writer.writeNonTerminal("LAndExp");
    } while (match(TokenType.AND));
  }

  // lOrExp: lAndExp (OR lAndExp)*;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseLOrExp() {
    do {
      parseLAndExp();
      writer.writeNonTerminal("LOrExp");
    } while (match(TokenType.OR));
  }

  // constExp: addExp;
  // FIRST SET: { IDENFR, INTCON, LPARENT, MINU, NOT, PLUS }
  // 常量表达式 ConstExp → AddExp 注：使用的 Ident 必须是常量
  private void parseConstExp() {
    parseAddExp();
    writer.writeNonTerminal("ConstExp");
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
