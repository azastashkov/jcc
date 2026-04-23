package io.jcc.cli.highlight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BashHighlighterTest {

    private final BashHighlighter h = new BashHighlighter();

    private List<Token> tokenize(String s) { return h.tokenize(s); }

    @Test
    void roundTripsLossless() {
        String src = "if [ -f x.txt ]; then\n  cat x.txt\nfi\n";
        StringBuilder sb = new StringBuilder();
        for (Token t : tokenize(src)) sb.append(t.text());
        assertThat(sb.toString()).isEqualTo(src);
    }

    @Test
    void recognizesControlKeywords() {
        for (String kw : List.of("if", "then", "else", "elif", "fi",
            "case", "esac", "for", "while", "until", "do", "done", "in",
            "function", "return", "break", "continue",
            "local", "export", "readonly", "declare", "unset")) {
            assertThat(tokenize(kw))
                .as("'%s' should be KEYWORD", kw)
                .extracting(Token::type)
                .containsExactly(TokenType.KEYWORD);
        }
    }

    @Test
    void plainNamesAreIdentifiers() {
        assertThat(tokenize("ls"))
            .extracting(Token::type)
            .containsExactly(TokenType.IDENTIFIER);
        assertThat(tokenize("my_func"))
            .extracting(Token::type)
            .containsExactly(TokenType.IDENTIFIER);
    }

    @Test
    void hashStartsLineComment() {
        List<Token> ts = tokenize("# this is a comment");
        assertThat(ts).containsExactly(new Token(TokenType.COMMENT, "# this is a comment"));
    }

    @Test
    void commentStopsAtNewline() {
        List<Token> ts = tokenize("# c\nls");
        assertThat(ts.get(0)).isEqualTo(new Token(TokenType.COMMENT, "# c"));
    }

    @Test
    void doubleQuotedString() {
        assertThat(tokenize("\"hello\""))
            .containsExactly(new Token(TokenType.STRING, "\"hello\""));
    }

    @Test
    void singleQuotedString() {
        assertThat(tokenize("'literal'"))
            .containsExactly(new Token(TokenType.STRING, "'literal'"));
    }

    @Test
    void simpleVariable() {
        assertThat(tokenize("$HOME"))
            .containsExactly(new Token(TokenType.VARIABLE, "$HOME"));
    }

    @Test
    void bracedVariable() {
        assertThat(tokenize("${PATH}"))
            .containsExactly(new Token(TokenType.VARIABLE, "${PATH}"));
    }

    @Test
    void positionalVariable() {
        assertThat(tokenize("$1"))
            .containsExactly(new Token(TokenType.VARIABLE, "$1"));
        assertThat(tokenize("$@"))
            .containsExactly(new Token(TokenType.VARIABLE, "$@"));
        assertThat(tokenize("$?"))
            .containsExactly(new Token(TokenType.VARIABLE, "$?"));
    }

    @Test
    void numbersAreRecognized() {
        assertThat(tokenize("42"))
            .containsExactly(new Token(TokenType.NUMBER, "42"));
    }

    @Test
    void operatorsTokenized() {
        // "|", "&&", "||", ";", ">"
        for (String op : List.of("|", "&&", "||", ";", ">", "<", ">>")) {
            List<Token> ts = tokenize(op);
            assertThat(ts).extracting(Token::type)
                .as("'%s' should be OPERATOR", op)
                .containsExactly(TokenType.OPERATOR);
        }
    }

    @Test
    void completeScriptSnippet() {
        String src = "for f in *.txt; do\n  echo \"$f\"\ndone";
        List<Token> ts = tokenize(src);
        // Spot-check: contains at least one of each key category
        assertThat(ts).extracting(Token::type)
            .contains(TokenType.KEYWORD, TokenType.STRING, TokenType.VARIABLE, TokenType.IDENTIFIER);
    }

    @Test
    void emptySource() {
        assertThat(tokenize("")).isEmpty();
    }
}
