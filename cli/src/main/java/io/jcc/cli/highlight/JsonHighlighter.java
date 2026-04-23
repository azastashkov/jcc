package io.jcc.cli.highlight;

import java.util.ArrayList;
import java.util.List;

public final class JsonHighlighter implements CodeHighlighter {

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

            if (c == '"') {
                int j = i + 1;
                while (j < n) {
                    char d = src.charAt(j);
                    if (d == '\\' && j + 1 < n) { j += 2; continue; }
                    if (d == '"') { j++; break; }
                    j++;
                }
                int k = j;
                while (k < n && Character.isWhitespace(src.charAt(k))) k++;
                TokenType type = (k < n && src.charAt(k) == ':')
                    ? TokenType.VARIABLE
                    : TokenType.STRING;
                tokens.add(new Token(type, src.substring(i, j)));
                i = j;
                continue;
            }

            if (Character.isDigit(c)
                || (c == '-' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                int j = i + 1;
                while (j < n) {
                    char d = src.charAt(j);
                    boolean isExpSign = (d == '+' || d == '-')
                        && j > i
                        && (src.charAt(j - 1) == 'e' || src.charAt(j - 1) == 'E');
                    if (Character.isDigit(d) || d == '.' || d == 'e' || d == 'E' || isExpSign) {
                        j++;
                    } else break;
                }
                tokens.add(new Token(TokenType.NUMBER, src.substring(i, j)));
                i = j;
                continue;
            }

            if (Character.isLetter(c)) {
                int j = i + 1;
                while (j < n && Character.isLetter(src.charAt(j))) j++;
                String word = src.substring(i, j);
                TokenType type = (word.equals("true") || word.equals("false") || word.equals("null"))
                    ? TokenType.KEYWORD
                    : TokenType.IDENTIFIER;
                tokens.add(new Token(type, word));
                i = j;
                continue;
            }

            if (",:[]{}".indexOf(c) >= 0) {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                i++;
                continue;
            }

            tokens.add(new Token(TokenType.OTHER, String.valueOf(c)));
            i++;
        }
        return tokens;
    }
}
