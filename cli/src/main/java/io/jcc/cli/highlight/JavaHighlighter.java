package io.jcc.cli.highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JavaHighlighter implements CodeHighlighter {

    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int", "interface",
        "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized",
        "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
        "var", "yield", "record", "sealed", "permits",
        "true", "false", "null"
    );

    private static final String OPERATOR_CHARS = "+-*/%=<>!&|^~?:;,.()[]{}";

    @Override
    public List<Token> tokenize(String src) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);

            if (Character.isWhitespace(c)) {
                int j = i;
                while (j < n && Character.isWhitespace(src.charAt(j))) j++;
                tokens.add(new Token(TokenType.OTHER, src.substring(i, j)));
                i = j;
                continue;
            }

            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int j = src.indexOf('\n', i);
                if (j < 0) j = n;
                tokens.add(new Token(TokenType.COMMENT, src.substring(i, j)));
                i = j;
                continue;
            }

            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int j = src.indexOf("*/", i + 2);
                if (j < 0) j = n; else j += 2;
                tokens.add(new Token(TokenType.COMMENT, src.substring(i, j)));
                i = j;
                continue;
            }

            if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                int j = src.indexOf("\"\"\"", i + 3);
                if (j < 0) j = n; else j += 3;
                tokens.add(new Token(TokenType.STRING, src.substring(i, j)));
                i = j;
                continue;
            }

            if (c == '"') {
                int j = i + 1;
                while (j < n) {
                    char d = src.charAt(j);
                    if (d == '\\' && j + 1 < n) { j += 2; continue; }
                    if (d == '"') { j++; break; }
                    if (d == '\n') break;
                    j++;
                }
                tokens.add(new Token(TokenType.STRING, src.substring(i, j)));
                i = j;
                continue;
            }

            if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(src.charAt(i + 1))) {
                int j = i + 1;
                while (j < n && (Character.isJavaIdentifierPart(src.charAt(j)) || src.charAt(j) == '.')) j++;
                tokens.add(new Token(TokenType.ANNOTATION, src.substring(i, j)));
                i = j;
                continue;
            }

            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n) {
                    char d = src.charAt(j);
                    if (Character.isLetterOrDigit(d) || d == '.' || d == '_') {
                        j++;
                    } else break;
                }
                tokens.add(new Token(TokenType.NUMBER, src.substring(i, j)));
                i = j;
                continue;
            }

            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(src.charAt(j))) j++;
                String word = src.substring(i, j);
                TokenType type = KEYWORDS.contains(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                tokens.add(new Token(type, word));
                i = j;
                continue;
            }

            if (OPERATOR_CHARS.indexOf(c) >= 0) {
                int j = i + 1;
                while (j < n && OPERATOR_CHARS.indexOf(src.charAt(j)) >= 0) j++;
                tokens.add(new Token(TokenType.OPERATOR, src.substring(i, j)));
                i = j;
                continue;
            }

            tokens.add(new Token(TokenType.OTHER, String.valueOf(c)));
            i++;
        }
        return tokens;
    }
}
