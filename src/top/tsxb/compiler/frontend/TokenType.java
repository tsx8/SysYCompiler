package top.tsxb.compiler.frontend;

/** The enum Token type, including all token types in the SysY Language. */
public enum TokenType {
  // Keywords
  CONSTTK, // const
  INTTK, // int
  STATICTK, // static
  BREAKTK, // break
  CONTINUETK, // continue
  IFTK, // if
  ELSETK, // else
  FORTK, // for
  RETURNTK, // return
  VOIDTK, // void
  MAINTK, // main
  PRINTFTK, // printf

  // Identifiers and constants
  IDENFR, // Ident
  INTCON, // IntConst
  STRCON, // StringConst

  // Operators and Punctuators
  NOT("!", 1),   // '!'
  AND("&&", 2),  // '&&'
  OR("||", 3),   // '||'
  PLUS("+", 4),  // '+'
  MINU("-", 4),  // '-'
  MULT("*", 5),  // '*'
  DIV("/", 5),   // '/'
  MOD("%", 5),   // '%'
  LSS("<", 6),   // '<'
  LEQ("<=", 6),  // '<='
  GRE(">", 6),   // '>'
  GEQ(">=", 6),  // '>='
  EQL("==", 7),  // '=='
  NEQ("!=", 7),  // '!='
  ASSIGN("="), // '='

  // Delimiters
  SEMICN, // ;
  COMMA, // ,
  LPARENT, // (
  RPARENT, // )
  LBRACK, // [
  RBRACK, // ]
  LBRACE, // {
  RBRACE, // }

  // EOF
  EOF;

  private final String repr;
  private final int priv;

  TokenType() {
    this.repr = null;
    this.priv = -1;
  }

  TokenType(String repr) {
    this.repr = repr;
    this.priv = -1;
  }

  TokenType(String repr, int priv) {
    this.repr = repr;
    this.priv = priv;
  }

  public int getPriv() {
    return priv;
  }
}
