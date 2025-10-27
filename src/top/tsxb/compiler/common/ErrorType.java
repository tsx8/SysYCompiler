package top.tsxb.compiler.common;

/**
 * The enum Error type.
 */
public enum ErrorType {
    INVALID_TOKEN("a", "Invalid Token"),

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
