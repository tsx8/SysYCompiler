package top.tsxb.compiler.frontend.parser;

import java.util.Arrays;
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

  private Token peek() {
    return tokens.peek();
  }

  private Token prev() {
    return tokens.previous();
  }

  private boolean isAtEnd() {
    return tokens.isAtEnd();
  }

  private void advance() {
    Token token = tokens.advance();
    writer.writeToken(token);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) {
      return false;
    }
    return peek().type() == type;
  }

  private boolean check(TokenType type, int offset) {
    if (isAtEnd()) {
      return false;
    }
    return tokens.peek(offset).type() == type;
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private void expect(TokenType type, String errorCode) {
    if (check(type)) {
      advance();
    } else {
      try {
        reporter.report(prev().line(), ErrorType.fromCode(errorCode), type.name());
      } catch (IllegalArgumentException e) {
        throw new SyntacticException("Unknown error code: " + errorCode);
      }
    }
  }

  private void expect(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return;
      }
    }
    throw new SyntacticException(
        "Unexpected token: " + peek().type() + ", expected one of: " + Arrays.toString(types)
        + " at line " + peek().line()
    );
  }

  // compUnit: decl* funcDef* mainFuncDef EOF;
  private void parseCompUnit() {
    while (!check(TokenType.MAINTK, 1)) {
      if (check(TokenType.LPARENT, 2)) {
        break;
      }
      parseDecl();
    }
    while (!check(TokenType.MAINTK, 1)) {
      parseFuncDef();
    }
    parseMainFuncDef();
    writer.writeNonTerminal("CompUnit");
  }

  // decl: constDecl | varDecl;
  private void parseDecl() {
    if (check(TokenType.CONSTTK)) {
      parseConstDecl();
    } else {
      parseVarDecl();
    }
    writer.writeNonTerminal("Decl");
  }

  // CONSTTK bType constDef (COMMA constDef)* SEMICN;
  private void parseConstDecl() {
    expect(TokenType.CONSTTK);
    parseBType();
    do {
      parseConstDef();
    } while (match(TokenType.COMMA));
    expect(TokenType.SEMICN, "i");
    writer.writeNonTerminal("ConstDecl");
  }

  // bType: INTTK;
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseBType() {
    expect(TokenType.INTTK);
    writer.writeNonTerminal("BType");
  }

  // constDef: IDENFR (LBRACK constExp RBRACK)? ASSIGN constInitVal;
  private void parseConstDef() {
    expect(TokenType.IDENFR);
    while (match(TokenType.LBRACK)) {
      parseConstExp();
      expect(TokenType.RBRACK, "k");
    }
    expect(TokenType.ASSIGN);
    parseConstInitVal();
    writer.writeNonTerminal("ConstDef");
  }

  // constExp: addExp;
  private void parseConstExp() {
    parseAddExp();
    writer.writeNonTerminal("ConstExp");
  }

  // addExp: mulExp ( (PLUS | MINU) mulExp)*;
  private void parseAddExp() {
    do {
      parseMulExp();
    } while (match(TokenType.PLUS, TokenType.MINU));
    writer.writeNonTerminal("AddExp");
  }

  // mulExp: unaryExp ( (MULT | DIV | MOD) unaryExp)*;
  private void parseMulExp() {
    do {
      parseUnaryExp();
    } while (match(TokenType.MULT, TokenType.DIV, TokenType.MOD));
    writer.writeNonTerminal("MulExp");
  }

  // unaryExp: primaryExp
  //         | IDENFR LPARENT funcRParams? RPARENT
  //         | unaryOp unaryExp;
  private void parseUnaryExp() {
    if (check(TokenType.IDENFR) && check(TokenType.LPARENT, 1)) {
      expect(TokenType.IDENFR);
      expect(TokenType.LPARENT);
      if (!check(TokenType.RPARENT)) {
        parseFuncRParams();
      }
      expect(TokenType.RPARENT, "j");
    } else if (match(TokenType.PLUS, TokenType.MINU, TokenType.NOT)) {
      parseUnaryOp();
      parseUnaryExp();
    } else {
      parsePrimaryExp();
    }
    writer.writeNonTerminal("UnaryExp");
  }

  // funcRParams: exp (COMMA exp)*;
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseFuncRParams() {
    do {
      parseExp();
    } while (match(TokenType.COMMA));
    writer.writeNonTerminal("FuncRParams");
  }

  // unaryOp: PLUS | MINU | NOT;
  private void parseUnaryOp() {
    expect(TokenType.PLUS, TokenType.MINU, TokenType.NOT);
    writer.writeNonTerminal("UnaryOp");
  }

  // primaryExp: LPARENT exp RPARENT | lVal | number;
  private void parsePrimaryExp() {
    if (match(TokenType.LPARENT)) {
      parseExp();
      expect(TokenType.RPARENT, "j");
    } else if (check(TokenType.IDENFR)) {
      parseLVal();
    } else {
      parseNumber();
    }
    writer.writeNonTerminal("PrimaryExp");
  }

  // lVal: IDENFR (LBRACK exp RBRACK)?;
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseLVal() {
    expect(TokenType.IDENFR);
    if (match(TokenType.LBRACK)) { // only one dimension supported
      parseExp();
      expect(TokenType.RBRACK, "k");
    }
    writer.writeNonTerminal("LVal");
  }

  // number: INTCON;
  private void parseNumber() {
    expect(TokenType.INTCON);
    writer.writeNonTerminal("Number");
  }

  // constInitVal: constExp | LBRACE (constExp (COMMA constExp)*)? RBRACE;
  private void parseConstInitVal() {
    if (match(TokenType.LBRACE)) {
      if (!check(TokenType.RBRACE)) {
        do {
          parseConstExp();
        } while (match(TokenType.COMMA));
      }
      expect(TokenType.RBRACE);
    } else {
      parseConstExp();
    }
    writer.writeNonTerminal("ConstInitVal");
  }

  // varDecl: STATICTK? bType varDef (COMMA varDef)* SEMICN;
  private void parseVarDecl() {
    match(TokenType.STATICTK);
    parseBType();
    do {
      parseVarDef();
    } while (match(TokenType.COMMA));
    expect(TokenType.SEMICN, "i");
    writer.writeNonTerminal("VarDecl");
  }

  // varDef: IDENFR (LBRACK constExp RBRACK)? (ASSIGN initVal)?;
  private void parseVarDef() {
    expect(TokenType.IDENFR);
    if (match(TokenType.LBRACK)) { // only one dimension supported
      parseConstExp();
      expect(TokenType.RBRACK, "k");
    }
    if (match(TokenType.ASSIGN)) {
      parseInitVal();
    }
    writer.writeNonTerminal("VarDef");
  }

  // initVal: exp | LBRACE (exp (COMMA exp)*)? RBRACE;
  private void parseInitVal() {
    if (match(TokenType.LBRACE)) {
      if (!check(TokenType.RBRACE)) {
        do {
          parseExp();
        } while (match(TokenType.COMMA));
      }
      expect(TokenType.RBRACE);
    } else {
      parseExp();
    }
    writer.writeNonTerminal("InitVal");
  }

  // exp: addExp;
  private void parseExp() {
    parseAddExp();
    writer.writeNonTerminal("Exp");
  }

  // funcDef: funcType IDENFR LPARENT funcFParams? RPARENT block;
  private void parseFuncDef() {
    parseFuncType();
    expect(TokenType.IDENFR);
    expect(TokenType.LPARENT);
    if (!check(TokenType.RPARENT)) {
      parseFuncFParams();
    }
    expect(TokenType.RPARENT, "j");
    parseBlock();
    writer.writeNonTerminal("FuncDef");
  }

  // funcType: VOIDTK | INTTK;
  private void parseFuncType() {
    match(TokenType.VOIDTK, TokenType.INTTK);
    writer.writeNonTerminal("FuncType");
  }

  // funcFParams: funcFParam (COMMA funcFParam)*;
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseFuncFParams() {
    do {
      parseFuncFParam();
    } while (match(TokenType.COMMA));
    writer.writeNonTerminal("FuncFParams");
  }

  // funcFParam: bType IDENFR (LBRACK RBRACK)?;
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseFuncFParam() {
    parseBType();
    expect(TokenType.IDENFR);
    if (match(TokenType.LBRACK)) { // only one dimension supported
      expect(TokenType.RBRACK, "k");
    }
    writer.writeNonTerminal("FuncFParam");
  }

  // block: LBRACE blockItem* RBRACE;
  private void parseBlock() {
    expect(TokenType.LBRACE);
    while (!check(TokenType.RBRACE)) {
      parseBlockItem();
    }
    expect(TokenType.RBRACE);
    writer.writeNonTerminal("Block");
  }

  // blockItem: decl | stmt;
  private void parseBlockItem() {
    if (check(TokenType.CONSTTK) || check(TokenType.INTTK)) {
      parseDecl();
    } else {
      parseStmt();
    }
    writer.writeNonTerminal("BlockItem");
  }

  // stmt: lVal ASSIGN exp SEMICN
  //     | exp? SEMICN
  //     | block
  //     | IFTK LPARENT cond RPARENT stmt (ELSETK stmt)?
  //     | FORTK LPARENT forStmt? SEMICN cond? SEMICN forStmt? RPARENT stmt
  //     | BREAKTK SEMICN
  //     | CONTINUETK SEMICN
  //     | RETURNTK exp? SEMICN
  //     | PRINTFTK LPARENT STRCON (COMMA exp)* RPARENT SEMICN
  //     ;
  private void parseStmt() {
    if (check(TokenType.LBRACE)) { // block
      parseBlock();
    } else if (match(TokenType.IFTK)) { // if
      expect(TokenType.LPARENT);
      parseCond();
      expect(TokenType.RPARENT, "j");
      parseStmt();
      if (match(TokenType.ELSETK)) {
        parseStmt();
      }
    } else if (match(TokenType.FORTK)) { // for
      expect(TokenType.LPARENT);
      if (!check(TokenType.SEMICN)) {
        parseForStmt();
      }
      expect(TokenType.SEMICN, "i");
      if (!check(TokenType.SEMICN)) {
        parseCond();
      }
      expect(TokenType.SEMICN, "i");
      if (!check(TokenType.RPARENT)) {
        parseForStmt();
      }
      expect(TokenType.RPARENT, "j");
      parseStmt();
    } else if (match(TokenType.BREAKTK, TokenType.CONTINUETK)) {
      expect(TokenType.SEMICN, "i");
    } else if (match(TokenType.RETURNTK)) {
      if (!check(TokenType.SEMICN)) {
        parseExp();
      }
      expect(TokenType.SEMICN, "i");
    } else if (match(TokenType.PRINTFTK)) {
      expect(TokenType.LPARENT);
      expect(TokenType.STRCON);
      while (match(TokenType.COMMA)) {
        parseExp();
      }
      expect(TokenType.RPARENT, "j");
      expect(TokenType.SEMICN, "i");
    } else if (check(TokenType.IDENFR) && check(TokenType.ASSIGN, 1)) {
      parseLVal();
      expect(TokenType.ASSIGN);
      parseExp();
      expect(TokenType.SEMICN, "i");
    } else {
      if (!check(TokenType.SEMICN)) {
        parseExp();
      }
      expect(TokenType.SEMICN, "i");
    }
    writer.writeNonTerminal("Stmt");
  }

  // cond: lOrExp;
  private void parseCond() {
    parseLOrExp();
    writer.writeNonTerminal("Cond");
  }

  // lOrExp: lAndExp (OR lAndExp)*;
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseLOrExp() {
    do {
      parseLAndExp();
    } while (match(TokenType.OR));
    writer.writeNonTerminal("LOrExp");
  }

  // lAndExp: eqExp (AND eqExp)*;
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private void parseLAndExp() {
    do {
      parseEqExp();
    } while (match(TokenType.AND));
    writer.writeNonTerminal("LAndExp");
  }

  // eqExp: relExp ( (EQL | NEQ) relExp)*;
  private void parseEqExp() {
    do {
      parseRelExp();
    } while (match(TokenType.EQL, TokenType.NEQ));
    writer.writeNonTerminal("EqExp");
  }

  // relExp: addExp ( (LSS | GRE | LEQ | GEQ) addExp)*;
  private void parseRelExp() {
    do {
      parseAddExp();
    } while (match(TokenType.LSS, TokenType.GRE, TokenType.LEQ, TokenType.GEQ));
    writer.writeNonTerminal("RelExp");
  }

  // forStmt: lVal ASSIGN exp (COMMA lVal ASSIGN exp)*;
  private void parseForStmt() {
    do {
      parseLVal();
      expect(TokenType.ASSIGN);
      parseExp();
    } while (match(TokenType.COMMA));
    writer.writeNonTerminal("ForStmt");
  }

  // MainFuncDef ::= 'int' 'main' '(' ')' Block
  private void parseMainFuncDef() {
    expect(TokenType.INTTK);
    expect(TokenType.MAINTK);
    expect(TokenType.LPARENT);
    expect(TokenType.RPARENT, "j");
    parseBlock();
    writer.writeNonTerminal("MainFuncDef");
  }
}
