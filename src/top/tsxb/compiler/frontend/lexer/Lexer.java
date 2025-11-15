package top.tsxb.compiler.frontend.lexer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.tsxb.compiler.common.ErrorReporter;
import top.tsxb.compiler.common.ErrorType;
import top.tsxb.compiler.ir.cst.Token;
import top.tsxb.compiler.ir.cst.TokenStream;
import top.tsxb.compiler.ir.cst.TokenType;

/**
 * The type Lexer. Scans the source code and produces tokens.
 */
public class Lexer {
    private static final Map<String,
        TokenType> KEYWORDS = Map.ofEntries(Map.entry("const", TokenType.CONSTTK), Map.entry("int", TokenType.INTTK),
            Map.entry("static", TokenType.STATICTK), Map.entry("break", TokenType.BREAKTK),
            Map.entry("continue", TokenType.CONTINUETK), Map.entry("if", TokenType.IFTK),
            Map.entry("else", TokenType.ELSETK), Map.entry("for", TokenType.FORTK),
            Map.entry("return", TokenType.RETURNTK), Map.entry("void", TokenType.VOIDTK),
            Map.entry("main", TokenType.MAINTK), Map.entry("printf", TokenType.PRINTFTK));

    private static final Map<String, String> TOKEN_PATTERNS = new LinkedHashMap<>();
    private static final Pattern TOKEN_PATTERN;

    static {
        TOKEN_PATTERNS.put("COMMENT", "//[^\n]*|/\\*.*?\\*/"); // //[^\n]* | /* .*? */
        TOKEN_PATTERNS.put("WHITESPACE", "\\s+"); // \s+
        TOKEN_PATTERNS.put("IDENFR", "[a-zA-Z_][a-zA-Z_0-9]*");
        TOKEN_PATTERNS.put("INTCON", "0|[1-9][0-9]*");
        TOKEN_PATTERNS.put("STRCON", "\"(%d|\\\\n|[ !#-\\[\\]-~])*\""); // "(%d | \n | [ ! #-[ ]-~ ])*"

        TOKEN_PATTERNS.put("LEQ", "<=");
        TOKEN_PATTERNS.put("GEQ", ">=");
        TOKEN_PATTERNS.put("EQL", "==");
        TOKEN_PATTERNS.put("NEQ", "!=");
        TOKEN_PATTERNS.put("AND", "&&");
        TOKEN_PATTERNS.put("OR", "\\|\\|"); // ||
        TOKEN_PATTERNS.put("LSS", "<");
        TOKEN_PATTERNS.put("GRE", ">");
        TOKEN_PATTERNS.put("ASSIGN", "=");
        TOKEN_PATTERNS.put("PLUS", "\\+"); // +
        TOKEN_PATTERNS.put("MINU", "-");
        TOKEN_PATTERNS.put("MULT", "\\*"); // *
        TOKEN_PATTERNS.put("DIV", "/");
        TOKEN_PATTERNS.put("MOD", "%");
        TOKEN_PATTERNS.put("NOT", "!");

        TOKEN_PATTERNS.put("LPARENT", "\\("); // (
        TOKEN_PATTERNS.put("RPARENT", "\\)"); // )
        TOKEN_PATTERNS.put("LBRACK", "\\["); // [
        TOKEN_PATTERNS.put("RBRACK", "\\]"); // ]
        TOKEN_PATTERNS.put("LBRACE", "\\{"); // {
        TOKEN_PATTERNS.put("RBRACE", "\\}"); // }
        TOKEN_PATTERNS.put("COMMA", ",");
        TOKEN_PATTERNS.put("SEMICN", ";");

        TOKEN_PATTERNS.put("ILLEGALAND", "&");
        TOKEN_PATTERNS.put("ILLEGALOR", "\\|"); // |

        TOKEN_PATTERNS.put("MISMATCH", ".");

        StringBuilder combinedPattern = new StringBuilder();
        for (Map.Entry<String, String> entry : TOKEN_PATTERNS.entrySet()) {
            combinedPattern.append(String.format("(?<%s>%s)|", entry.getKey(), entry.getValue()));
        }
        combinedPattern.setLength(combinedPattern.length() - 1);

        TOKEN_PATTERN = Pattern.compile(combinedPattern.toString(), Pattern.DOTALL);
    }

    private final String source;
    private final ErrorReporter errorReporter;
    private final List<Token> tokens = new ArrayList<>();

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
    public TokenStream scan() {
        Matcher matcher = TOKEN_PATTERN.matcher(source);
        while (matcher.find()) {
            String lexeme = matcher.group();
            String groupName = findMatchedGroup(matcher);

            switch (groupName) {
                case "WHITESPACE", "COMMENT" -> line += countNewlines(lexeme);
                case "IDENFR" -> addToken(KEYWORDS.getOrDefault(lexeme, TokenType.IDENFR), lexeme);
                case "INTCON" -> addToken(TokenType.INTCON, lexeme);
                case "ILLEGALAND" -> {
                    errorReporter.report(line, ErrorType.fromCode("a"), lexeme);
                    addToken(TokenType.AND, "&&");
                }
                case "ILLEGALOR" -> {
                    errorReporter.report(line, ErrorType.fromCode("a"), lexeme);
                    addToken(TokenType.OR, "||");
                }
                case "MISMATCH" -> throw new LexicalException("Unexpected character: " + lexeme + " at line " + line);
                default -> {
                    TokenType type = TokenType.valueOf(groupName);
                    addToken(type, lexeme);
                }
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line));
        return new TokenStream(tokens);
    }

    private String findMatchedGroup(Matcher matcher) {
        for (String groupName : TOKEN_PATTERNS.keySet()) {
            if (matcher.group(groupName) != null) {
                return groupName;
            }
        }
        throw new LexicalException("Unexpected group: " + matcher.group());
    }

    private void addToken(TokenType type, String lexeme) {
        tokens.add(new Token(type, lexeme, line));
    }

    private int countNewlines(String text) {
        int newlines = 0;
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                newlines++;
            }
        }
        return newlines;
    }
}
