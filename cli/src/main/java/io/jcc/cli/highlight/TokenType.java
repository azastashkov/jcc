package io.jcc.cli.highlight;

public enum TokenType {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    ANNOTATION,
    OPERATOR,
    /** Variables, JSON property keys — anything that should be the JetBrains purple. */
    VARIABLE,
    IDENTIFIER,
    OTHER
}
