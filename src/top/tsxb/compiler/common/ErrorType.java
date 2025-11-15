package top.tsxb.compiler.common;

/**
 * The enum Error type.
 */
public enum ErrorType {
    // Lexer Error
    INVALID_TOKEN("a", "Invalid Token"),

    // Semantic Error
    NAME_REDEFINITION("b", "Name redefinition"), NAME_NOT_DEFINED("c", "Name not defined"),
    FUNC_ARG_COUNT_MISMATCH("d", "Function argument count mismatch"),
    FUNC_ARG_TYPE_MISMATCH("e", "Function argument type mismatch"),
    VOID_FUNC_WITH_RETURN_VALUE("f", "Void function should not return a value"),
    INT_FUNC_MISSING_RETURN("g", "Int function missing return statement"),
    MODIFY_CONST("h", "Cannot modify a constant value"),
    PRINTF_ARG_COUNT_MISMATCH("l", "Printf argument count mismatch"),
    BREAK_CONTINUE_OUTSIDE_LOOP("m", "Break/Continue statement outside of loop"),

    // Parser Error
    MISSING_SEMICOLON("i", "Missing Semicolon"), MISSING_RPAREN("j", "Missing Right Parenthesis"),
    MISSING_RBRACK("k", "Missing Right Bracket");

    private final String code;
    private final String description;

    ErrorType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * From code error type.
     *
     * @param code the code
     * @return the error type
     */
    public static ErrorType fromCode(String code) {
        for (ErrorType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown error code: " + code);
    }

    public String getCode() {
        return code;
    }

    /**
     * Gets description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
