package io.jcc.cli.highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BashHighlighter implements CodeHighlighter {

    private static final Set<String> KEYWORDS = Set.of(
        "if", "then", "else", "elif", "fi",
        "case", "esac", "for", "select", "while", "until", "do", "done", "in",
        "function", "time", "coproc", "return",
        "break", "continue",
        "local", "export", "readonly", "declare", "typeset",
        "eval", "exec", "set", "shift", "source", "unset", "trap"
    );

    private static final String OP_CHARS = "|&;<>(){}[]!=+-*/%^?";
    private static final String OP_CONT  = "|&;<>=";

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

            if (c == '#') {
                int j = src.indexOf('\n', i);
                if (j < 0) j = n;
                tokens.add(new Token(TokenType.COMMENT, src.substring(i, j)));
                i = j;
                continue;
            }

            if (c == '"') {
                int j = i + 1;
                int segStart = i;
                while (j < n) {
                    char d = src.charAt(j);
                    if (d == '\\' && j + 1 < n) { j += 2; continue; }
                    if (d == '"') { j++; break; }
                    if (d == '$' && j + 1 < n) {
                        int varEnd = scanVariableEnd(src, j, n);
                        if (varEnd > 0) {
                            if (j > segStart) {
                                tokens.add(new Token(TokenType.STRING, src.substring(segStart, j)));
                            }
                            tokens.add(new Token(TokenType.VARIABLE, src.substring(j, varEnd)));
                            j = varEnd;
                            segStart = j;
                            continue;
                        }
                    }
                    j++;
                }
                if (j > segStart) {
                    tokens.add(new Token(TokenType.STRING, src.substring(segStart, j)));
                }
                i = j;
                continue;
            }

            if (c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != '\'') j++;
                if (j < n) j++;
                tokens.add(new Token(TokenType.STRING, src.substring(i, j)));
                i = j;
                continue;
            }

            if (c == '$' && i + 1 < n) {
                int varEnd = scanVariableEnd(src, i, n);
                if (varEnd > 0) {
                    tokens.add(new Token(TokenType.VARIABLE, src.substring(i, varEnd)));
                    i = varEnd;
                    continue;
                }
            }

            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '.' || src.charAt(j) == '_')) j++;
                tokens.add(new Token(TokenType.NUMBER, src.substring(i, j)));
                i = j;
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) j++;
                String word = src.substring(i, j);
                TokenType type = KEYWORDS.contains(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                tokens.add(new Token(type, word));
                i = j;
                continue;
            }

            if (OP_CHARS.indexOf(c) >= 0) {
                int j = i + 1;
                while (j < n && OP_CONT.indexOf(src.charAt(j)) >= 0) j++;
                tokens.add(new Token(TokenType.OPERATOR, src.substring(i, j)));
                i = j;
                continue;
            }

            tokens.add(new Token(TokenType.OTHER, String.valueOf(c)));
            i++;
        }
        return tokens;
    }

    /** Returns end index past the variable starting at {@code dollarPos} (the '$'), or -1 if not a variable. */
    private static int scanVariableEnd(String src, int dollarPos, int n) {
        if (dollarPos + 1 >= n) return -1;
        char next = src.charAt(dollarPos + 1);
        if (next == '{') {
            int close = src.indexOf('}', dollarPos + 2);
            return close < 0 ? n : close + 1;
        }
        if (Character.isLetter(next) || next == '_') {
            int j = dollarPos + 2;
            while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) j++;
            return j;
        }
        if (next == '@' || next == '*' || next == '?' || next == '$'
            || next == '!' || next == '#' || next == '-' || Character.isDigit(next)) {
            return dollarPos + 2;
        }
        return -1;
    }
}
