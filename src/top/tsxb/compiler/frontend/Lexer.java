package top.tsxb.compiler.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;

/** The type Lexer. Scans the source code and produces tokens. */
public class Lexer {
  private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
      Map.entry("const", TokenType.CONSTTK),
      Map.entry("int", TokenType.INTTK),
      Map.entry("static", TokenType.STATICTK),
      Map.entry("break", TokenType.BREAKTK),
      Map.entry("continue", TokenType.CONTINUETK),
      Map.entry("if", TokenType.IFTK),
      Map.entry("else", TokenType.ELSETK),
      Map.entry("for", TokenType.FORTK),
      Map.entry("return", TokenType.RETURNTK),
      Map.entry("void", TokenType.VOIDTK),
      Map.entry("main", TokenType.MAINTK),
      Map.entry("printf", TokenType.PRINTFTK)
  );

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
      case '(' -> addToken(TokenType.LPARENT);
      case ')' -> addToken(TokenType.RPARENT);
      case '[' -> addToken(TokenType.LBRACK);
      case ']' -> addToken(TokenType.RBRACK);
      case '{' -> addToken(TokenType.LBRACE);
      case '}' -> addToken(TokenType.RBRACE);
      case ',' -> addToken(TokenType.COMMA);
      case ';' -> addToken(TokenType.SEMICN);
      case '+' -> addToken(TokenType.PLUS);
      case '-' -> addToken(TokenType.MINU);
      case '*' -> addToken(TokenType.MULT);
      case '%' -> addToken(TokenType.MOD);
      case '!' -> addToken(match('=') ? TokenType.NEQ : TokenType.NOT);
      case '=' -> addToken(match('=') ? TokenType.EQL : TokenType.ASSIGN);
      case '<' -> addToken(match('=') ? TokenType.LEQ : TokenType.LSS);
      case '>' -> addToken(match('=') ? TokenType.GEQ : TokenType.GRE);
      case '&' -> {
        if (match('&')) {
          addToken(TokenType.AND);
        } else {
          errorReporter.report(line, ErrorType.INVALID_TOKEN, "&");
        }
      }
      case '|' -> {
        if (match('|')) {
          addToken(TokenType.OR);
        } else {
          errorReporter.report(line, ErrorType.INVALID_TOKEN, "|");
        }
      }
      case ' ', '\r', '\t' -> {
        // Ignore whitespace.
      }
      case '/' -> handleSlash();
      case '\n' -> line++;
      case '"' -> handleString();
      default -> {
        if (Character.isDigit(c)) {
          handleNumber();
        } else if (Character.isLetter(c) || c == '_') {
          handleIdentifier();
        } else {
          // should not reach here
          throw new LexicalException("Unexpected character: " + c + " at line " + line);
        }
      }
    }
  }

  private void handleIdentifier() {
    while (Character.isLetterOrDigit(peek()) || peek() == '_') {
      advance();
    }
    String lexeme = source.substring(start, current);
    TokenType type = KEYWORDS.getOrDefault(lexeme, TokenType.IDENFR);
    addToken(type);
  }

  private void handleNumber() {
    if (source.charAt(start) == '0' && current < source.length() && Character.isDigit(peek())) {
      throw new LexicalException("Invalid number format at line " + line);
    } else {
      while (Character.isDigit(peek())) {
        advance();
      }
    }
    String lexeme = source.substring(start, current);
    addToken(TokenType.INTCON, Integer.parseInt(lexeme));
  }

  private void handleString() {
    while (peek() != '"' && !isAtEnd()) {
      char c = peek();
      if (c == '\n') {
        throw new LexicalException("Unterminated string: literal newline found at line " + line);
      }
      if (c == '\\') {
        advance();
        if (peek() == 'n') {
          advance();
        } else {
          throw new LexicalException("Invalid escape sequence in string literal at line " + line);
        }
      } else if (c == '%') {
        advance();
        if (peek() == 'd') {
          advance();
        } else {
          throw new LexicalException("Invalid format specifier in string literal at line " + line);
        }
      } else {
        if (isNormalChar(c)) {
          advance();
        } else {
          throw new LexicalException("Illegal character in string literal at line " + line);
        }
      }
    }

    if (isAtEnd()) {
      throw new LexicalException("Unterminated string starting at line " + line);
    }
    // Consume the closing ".
    advance();
    String value = source.substring(start, current);
    addToken(TokenType.STRCON, value);
  }

  private boolean isNormalChar(char c) {
    // <NormalChar> → 十进制编码为32,33,40-126的ASCII字符
    return c == 32 || c == 33 || c >= 40 && c <= 126 && c != '\\';
  }

  private void handleSlash() {
    if (match('/')) { // Single-line comment
      while (peek() != '\n' && !isAtEnd()) {
        advance();
      }
    } else if (match('*')) { // Multi-line comment
      int blockCommentStartLine = line;
      while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
        if (peek() == '\n') {
          line++;
        }
        advance();
      }
      if (isAtEnd()) {
        throw new LexicalException(
            "Unterminated block comment starting at line " + blockCommentStartLine);
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
}
