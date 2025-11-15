// SysY Grammar File for ANTLR
grammar SysY;

compUnit: decl* funcDef* mainFuncDef EOF;

decl: constDecl | varDecl;

constDecl: CONSTTK bType constDef (COMMA constDef)* SEMICN;
bType: INTTK;
constDef: IDENFR (LBRACK constExp RBRACK)? ASSIGN constInitVal;
constInitVal: constExp | LBRACE (constExp (COMMA constExp)*)? RBRACE;

varDecl: STATICTK? bType varDef (COMMA varDef)* SEMICN;
varDef: IDENFR (LBRACK constExp RBRACK)? (ASSIGN initVal)?;
initVal: exp | LBRACE (exp (COMMA exp)*)? RBRACE;

funcDef: funcType IDENFR LPARENT funcFParams? RPARENT block;
mainFuncDef: INTTK MAINTK LPARENT RPARENT block;
funcType: VOIDTK | INTTK;

funcFParams: funcFParam (COMMA funcFParam)*;
funcFParam: bType IDENFR (LBRACK RBRACK)?;

block: LBRACE blockItem* RBRACE;
blockItem: decl | stmt;

stmt: lVal ASSIGN exp SEMICN
    | exp? SEMICN
    | block
    | IFTK LPARENT cond RPARENT stmt (ELSETK stmt)?
    | FORTK LPARENT forLoopStmt? SEMICN cond? SEMICN forLoopStmt? RPARENT stmt
    | BREAKTK SEMICN
    | CONTINUETK SEMICN
    | RETURNTK exp? SEMICN
    | PRINTFTK LPARENT STRCON (COMMA exp)* RPARENT SEMICN
    ;

forLoopStmt: lVal ASSIGN exp (COMMA lVal ASSIGN exp)*;
exp: addExp;
cond: lOrExp;
lVal: IDENFR (LBRACK exp RBRACK)?;
primaryExp: LPARENT exp RPARENT | lVal | number;
number: INTCON;

unaryExp: primaryExp
        | IDENFR LPARENT funcRParams? RPARENT
        | unaryOp unaryExp;

unaryOp: PLUS | MINU | NOT;
funcRParams: exp (COMMA exp)*;

mulExp: unaryExp ( (MULT | DIV | MOD) unaryExp)*;
addExp: mulExp ( (PLUS | MINU) mulExp)*;
relExp: addExp ( (LSS | GRE | LEQ | GEQ) addExp)*;
eqExp: relExp ( (EQL | NEQ) relExp)*;
lAndExp: eqExp (AND eqExp)*;
lOrExp: lAndExp (OR lAndExp)*;
constExp: addExp;

CONSTTK:    'const';
INTTK:      'int';
STATICTK:   'static';
BREAKTK:    'break';
CONTINUETK: 'continue';
IFTK:       'if';
ELSETK:     'else';
FORTK:      'for';
RETURNTK:   'return';
VOIDTK:     'void';
MAINTK:     'main';
PRINTFTK:   'printf';

IDENFR:     [a-zA-Z_] [a-zA-Z_0-9]*;
INTCON:     '0' | [1-9] [0-9]*;

fragment FormatChar: '%' 'd';
fragment NormalChar: ' ' | '!' | '#'..'[' | ']'..'~' | '\\n';
STRCON: '"' (FormatChar | NormalChar)* '"';

PLUS:   '+';
MINU:   '-';
MULT:   '*';
DIV:    '/';
MOD:    '%';
LSS:    '<';
LEQ:    '<=';
GRE:    '>';
GEQ:    '>=';
EQL:    '==';
NEQ:    '!=';
ASSIGN: '=';
NOT:    '!';
AND:    '&&';
OR:     '||';

SEMICN:  ';';
COMMA:   ',';
LPARENT: '(';
RPARENT: ')';
LBRACK:  '[';
RBRACK:  ']';
LBRACE:  '{';
RBRACE:  '}';

LINE_COMMENT:  '//' (~'\n')* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;