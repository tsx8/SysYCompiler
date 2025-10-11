package top.tsxb.compiler.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;

/** The type Lexer. Scans the source code and produces tokens. */
public class Lexer {
  private static final Map<String, TokenType> KEYWORDS;

  static {
    KEYWORDS = new HashMap<>();
    // To put keywords into the map
    KEYWORDS.put("const", TokenType.CONSTTK);
    KEYWORDS.put("int", TokenType.INTTK);
    KEYWORDS.put("static", TokenType.STATICTK);
    KEYWORDS.put("break", TokenType.BREAKTK);
    KEYWORDS.put("continue", TokenType.CONTINUETK);
    KEYWORDS.put("if", TokenType.IFTK);
    KEYWORDS.put("else", TokenType.ELSETK);
    KEYWORDS.put("for", TokenType.FORTK);
    KEYWORDS.put("return", TokenType.RETURNTK);
    KEYWORDS.put("void", TokenType.VOIDTK);
    KEYWORDS.put("main", TokenType.MAINTK);
    KEYWORDS.put("printf", TokenType.PRINTFTK);
  }

  private final String source;
  private final ErrorReporter errorReporter;
  private final List<Token> tokens = new ArrayList<>();

  private int start = 0;
  private int current = 0;
  private int line = 1;

  /**
   * Instantiates a new Lexer.
   *
   * @param source the source
   * @param errorReporter the error reporter
   */
  public Lexer(String source, ErrorReporter errorReporter) {
    this.source = source;
    this.errorReporter = errorReporter;
  }

  /**
   * Scans all tokens from the source code.
   *
   * @return A list of scanned tokens.
   */
  public List<Token> scan() {
    while (!isAtEnd()) {
      start = current;
      scanToken();
    }
    tokens.add(new Token(TokenType.EOF, "", null, line));
    return tokens;
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(':
        addToken(TokenType.LPARENT);
        break;
      case ')':
        addToken(TokenType.RPARENT);
        break;
      case '[':
        addToken(TokenType.LBRACK);
        break;
      case ']':
        addToken(TokenType.RBRACK);
        break;
      case '{':
        addToken(TokenType.LBRACE);
        break;
      case '}':
        addToken(TokenType.RBRACE);
        break;
      case ',':
        addToken(TokenType.COMMA);
        break;
      case ';':
        addToken(TokenType.SEMICN);
        break;
      case '+':
        addToken(TokenType.PLUS);
        break;
      case '-':
        addToken(TokenType.MINU);
        break;
      case '*':
        addToken(TokenType.MULT);
        break;
      case '%':
        addToken(TokenType.MOD);
        break;
      case '!':
        addToken(match('=') ? TokenType.NEQ : TokenType.NOT);
        break;
      case '=':
        addToken(match('=') ? TokenType.EQL : TokenType.ASSIGN);
        break;
      case '<':
        addToken(match('=') ? TokenType.LEQ : TokenType.LSS);
        break;
      case '>':
        addToken(match('=') ? TokenType.GEQ : TokenType.GRE);
        break;

      case '&':
        if (match('&')) {
          addToken(TokenType.AND);
        } else {
          errorReporter.report(line, ErrorType.INVALID_TOKEN, "&");
        }
        break;
      case '|':
        if (match('|')) {
          addToken(TokenType.OR);
        } else {
          errorReporter.report(line, ErrorType.INVALID_TOKEN, "|");
        }
        break;
      case '/':
        handleSlash();
        break;
      case ' ', '\r', '\t':
        // Ignore whitespace.
        break;
      case '\n':
        line++;
        break;
      case '"':
        handleString();
        break;
      default:
        if (isDigit(c)) {
          handleNumber();
        } else if (isAlpha(c)) {
          handleIdentifier();
        } else {
          // should not reach here
          throw new RuntimeException("Unexpected character: " + c + " at line " + line);
        }
        break;
    }
  }

  private void handleIdentifier() {
    while (isAlnum(peek())) {
      advance();
    }
    String lexeme = source.substring(start, current);
    TokenType type = KEYWORDS.getOrDefault(lexeme, TokenType.IDENFR);
    addToken(type);
  }

  private void handleNumber() {
    while (isDigit(peek())) {
      advance();
    }
    String lexeme = source.substring(start, current);
    addToken(TokenType.INTCON, Integer.parseInt(lexeme));
  }

  private void handleString() {
    while (peek() != '"' && !isAtEnd()) {
      if (peek() == '\n') {
        line++;
      }
      advance();
    }
    if (isAtEnd()) {
      throw new RuntimeException("Unterminated string at line " + line);
    }
    // Consume the closing ".
    advance();
    String value = source.substring(start, current);
    addToken(TokenType.STRCON, value);
  }

  private void handleSlash() {
    if (match('/')) { // Single-line comment
      while (peek() != '\n' && !isAtEnd()) {
        advance();
      }
    } else if (match('*')) { // Multi-line comment
      while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
        if (peek() == '\n') {
          line++;
        }
        advance();
      }
      if (isAtEnd()) {
        throw new RuntimeException("Unexpected end of file at line " + line);
      }
      // consume '*' and '/'
      advance();
      advance();
    } else {
      addToken(TokenType.DIV);
    }
  }

  private char peek() {
    if (isAtEnd()) {
      return '\0';
    }
    return source.charAt(current);
  }

  private char peekNext() {
    if (current + 1 >= source.length()) {
      return '\0';
    }
    return source.charAt(current + 1);
  }

  private boolean isAtEnd() {
    return current >= source.length();
  }

  private char advance() {
    return source.charAt(current++);
  }

  private void addToken(TokenType type) {
    addToken(type, null);
  }

  private void addToken(TokenType type, Object value) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, value, line));
  }

  private boolean match(char expected) {
    if (isAtEnd()) {
      return false;
    }
    if (source.charAt(current) != expected) {
      return false;
    }
    current++;
    return true;
  }

  private boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private boolean isAlnum(char c) {
    return isAlpha(c) || isDigit(c);
  }
}
