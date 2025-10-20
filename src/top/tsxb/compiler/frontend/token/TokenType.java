package top.tsxb.compiler.frontend.token;

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
  NOT, // '!'
  AND, // '&&'
  OR, // '||'
  PLUS, // '+'
  MINU, // '-'
  MULT, // '*'
  DIV, // '/'
  MOD, // '%'
  LSS, // '<'
  LEQ, // '<='
  GRE, // '>'
  GEQ, // '>='
  EQL, // '=='
  NEQ, // '!='
  ASSIGN, // '='

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
  EOF
}
