package io.jcc.cli.highlight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonHighlighterTest {

    private final JsonHighlighter h = new JsonHighlighter();

    private List<Token> tokenize(String s) { return h.tokenize(s); }

    @Test
    void roundTripsLossless() {
        String src = "{\n  \"name\": \"foo\",\n  \"count\": 42,\n  \"ok\": true\n}";
        StringBuilder sb = new StringBuilder();
        for (Token t : tokenize(src)) sb.append(t.text());
        assertThat(sb.toString()).isEqualTo(src);
    }

    @Test
    void propertyKeyIsVariable() {
        List<Token> ts = tokenize("\"name\": \"foo\"");
        assertThat(ts.get(0)).isEqualTo(new Token(TokenType.VARIABLE, "\"name\""));
        // The value side
        Token last = ts.get(ts.size() - 1);
        assertThat(last).isEqualTo(new Token(TokenType.STRING, "\"foo\""));
    }

    @Test
    void keyDetectionAllowsWhitespaceBeforeColon() {
        List<Token> ts = tokenize("\"k\"   : 1");
        assertThat(ts.get(0)).isEqualTo(new Token(TokenType.VARIABLE, "\"k\""));
    }

    @Test
    void valueWithoutColonIsString() {
        List<Token> ts = tokenize("[\"a\", \"b\"]");
        // Both strings should be STRING (values, not keys)
        long stringCount = ts.stream().filter(t -> t.type() == TokenType.STRING).count();
        long variableCount = ts.stream().filter(t -> t.type() == TokenType.VARIABLE).count();
        assertThat(stringCount).isEqualTo(2);
        assertThat(variableCount).isZero();
    }

    @Test
    void numbersIncludingNegativeAndDecimal() {
        for (String n : List.of("0", "42", "-7", "3.14", "1e10", "-1.5e-3")) {
            List<Token> ts = tokenize(n);
            assertThat(ts).extracting(Token::type)
                .as("'%s' should be NUMBER", n)
                .containsExactly(TokenType.NUMBER);
        }
    }

    @Test
    void trueFalseNullAreKeywords() {
        for (String kw : List.of("true", "false", "null")) {
            assertThat(tokenize(kw))
                .as("'%s' should be KEYWORD", kw)
                .extracting(Token::type)
                .containsExactly(TokenType.KEYWORD);
        }
    }

    @Test
    void bracketsAndCommasAreOperators() {
        for (String op : List.of("{", "}", "[", "]", ",", ":")) {
            assertThat(tokenize(op))
                .extracting(Token::type)
                .containsExactly(TokenType.OPERATOR);
        }
    }

    @Test
    void escapedQuoteInString() {
        assertThat(tokenize("\"a\\\"b\""))
            .extracting(Token::type)
            .containsExactly(TokenType.STRING);
    }

    @Test
    void completeObjectSnippet() {
        String src = "{\n  \"name\": \"alice\",\n  \"age\": 30,\n  \"admin\": false,\n  \"tags\": [\"x\", \"y\"]\n}";
        List<Token> ts = tokenize(src);
        // Spot-check categories
        assertThat(ts).extracting(Token::type)
            .contains(TokenType.OPERATOR, TokenType.VARIABLE, TokenType.STRING, TokenType.NUMBER, TokenType.KEYWORD);
        // "name", "age", "admin", "tags" must be VARIABLE (keys)
        long keys = ts.stream()
            .filter(t -> t.type() == TokenType.VARIABLE)
            .count();
        assertThat(keys).isEqualTo(4);
    }
}
