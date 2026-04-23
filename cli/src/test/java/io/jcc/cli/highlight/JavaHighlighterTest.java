package io.jcc.cli.highlight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaHighlighterTest {

    private final JavaHighlighter h = new JavaHighlighter();

    private List<Token> tokenize(String src) {
        return h.tokenize(src);
    }

    private static Token tok(TokenType t, String s) {
        return new Token(t, s);
    }

    @Test
    void roundTripsSourceLossless() {
        String src = "public class Foo {\n    int x = 42;\n}\n";
        StringBuilder sb = new StringBuilder();
        for (Token t : tokenize(src)) sb.append(t.text());
        assertThat(sb.toString()).isEqualTo(src);
    }

    @Test
    void recognizesCommonKeywords() {
        for (String kw : List.of(
            "public", "private", "protected", "class", "interface", "enum",
            "void", "int", "long", "boolean", "static", "final", "abstract",
            "if", "else", "for", "while", "return", "new", "this", "super",
            "true", "false", "null", "import", "package")) {
            assertThat(tokenize(kw))
                .as("'%s' should be a KEYWORD", kw)
                .extracting(Token::type)
                .containsExactly(TokenType.KEYWORD);
        }
    }

    @Test
    void recognizesModernKeywords() {
        for (String kw : List.of("var", "yield", "record", "sealed", "permits")) {
            assertThat(tokenize(kw))
                .as("'%s' should be a KEYWORD", kw)
                .extracting(Token::type)
                .containsExactly(TokenType.KEYWORD);
        }
    }

    @Test
    void identifiersAreNotKeywords() {
        for (String id : List.of("foo", "MyClass", "aClass", "voidly", "foo123", "_x")) {
            assertThat(tokenize(id))
                .as("'%s' should be IDENTIFIER", id)
                .extracting(Token::type)
                .containsExactly(TokenType.IDENTIFIER);
        }
    }

    @Test
    void simpleStringLiteral() {
        List<Token> ts = tokenize("\"hello\"");
        assertThat(ts).containsExactly(tok(TokenType.STRING, "\"hello\""));
    }

    @Test
    void stringWithEscapedQuote() {
        List<Token> ts = tokenize("\"a\\\"b\"");
        assertThat(ts).containsExactly(tok(TokenType.STRING, "\"a\\\"b\""));
    }

    @Test
    void stringStopsAtUnterminatedNewline() {
        List<Token> ts = tokenize("\"unterminated\nrest");
        assertThat(ts.get(0).type()).isEqualTo(TokenType.STRING);
        assertThat(ts.get(0).text()).isEqualTo("\"unterminated");
    }

    @Test
    void textBlock() {
        String src = "\"\"\"\nhello\n\"\"\"";
        List<Token> ts = tokenize(src);
        assertThat(ts).containsExactly(tok(TokenType.STRING, src));
    }

    @Test
    void lineComment() {
        List<Token> ts = tokenize("// hello world");
        assertThat(ts).containsExactly(tok(TokenType.COMMENT, "// hello world"));
    }

    @Test
    void lineCommentStopsAtNewline() {
        List<Token> ts = tokenize("// foo\nbar");
        assertThat(ts.get(0)).isEqualTo(tok(TokenType.COMMENT, "// foo"));
    }

    @Test
    void blockComment() {
        List<Token> ts = tokenize("/* a\nb */");
        assertThat(ts).containsExactly(tok(TokenType.COMMENT, "/* a\nb */"));
    }

    @Test
    void javadocComment() {
        List<Token> ts = tokenize("/** docs */");
        assertThat(ts).containsExactly(tok(TokenType.COMMENT, "/** docs */"));
    }

    @Test
    void integerLiteral() {
        assertThat(tokenize("42")).containsExactly(tok(TokenType.NUMBER, "42"));
    }

    @Test
    void longLiteral() {
        assertThat(tokenize("100L")).containsExactly(tok(TokenType.NUMBER, "100L"));
    }

    @Test
    void floatingPointLiteral() {
        assertThat(tokenize("3.14")).containsExactly(tok(TokenType.NUMBER, "3.14"));
        assertThat(tokenize("1.5f")).containsExactly(tok(TokenType.NUMBER, "1.5f"));
    }

    @Test
    void hexLiteral() {
        assertThat(tokenize("0xFF")).containsExactly(tok(TokenType.NUMBER, "0xFF"));
    }

    @Test
    void underscoreLiteral() {
        assertThat(tokenize("1_000_000")).containsExactly(tok(TokenType.NUMBER, "1_000_000"));
    }

    @Test
    void simpleAnnotation() {
        List<Token> ts = tokenize("@Override");
        assertThat(ts).containsExactly(tok(TokenType.ANNOTATION, "@Override"));
    }

    @Test
    void qualifiedAnnotation() {
        List<Token> ts = tokenize("@org.junit.Test");
        assertThat(ts.get(0)).isEqualTo(tok(TokenType.ANNOTATION, "@org.junit.Test"));
    }

    @Test
    void classDeclarationFullExample() {
        String src = "public final class Foo { int x = 42; }";
        List<Token> ts = tokenize(src);
        // Spot-check a few tokens
        assertThat(ts).extracting(Token::type, Token::text)
            .contains(
                org.assertj.core.api.Assertions.tuple(TokenType.KEYWORD, "public"),
                org.assertj.core.api.Assertions.tuple(TokenType.KEYWORD, "final"),
                org.assertj.core.api.Assertions.tuple(TokenType.KEYWORD, "class"),
                org.assertj.core.api.Assertions.tuple(TokenType.IDENTIFIER, "Foo"),
                org.assertj.core.api.Assertions.tuple(TokenType.KEYWORD, "int"),
                org.assertj.core.api.Assertions.tuple(TokenType.IDENTIFIER, "x"),
                org.assertj.core.api.Assertions.tuple(TokenType.NUMBER, "42")
            );
    }

    @Test
    void operatorsTokenizedAsOperator() {
        List<Token> ts = tokenize("a + b == c");
        // Operators may be grouped or single; just verify they're OPERATOR/OTHER, not IDENTIFIER
        for (Token t : ts) {
            if (t.text().contains("+") || t.text().contains("=")) {
                assertThat(t.type()).isEqualTo(TokenType.OPERATOR);
            }
        }
    }

    @Test
    void emptySource() {
        assertThat(tokenize("")).isEmpty();
    }
}
