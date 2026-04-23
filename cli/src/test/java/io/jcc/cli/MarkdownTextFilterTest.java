package io.jcc.cli;

import io.jcc.cli.highlight.HighlighterRegistry;
import io.jcc.cli.highlight.JavaHighlighter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownTextFilterTest {

    private ByteArrayOutputStream buf;
    private PrintStream out;
    private HighlighterRegistry registry;

    @BeforeEach
    void setup() {
        buf = new ByteArrayOutputStream();
        out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        registry = HighlighterRegistry.defaults();
    }

    private String captured() {
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void plainProsePassesThroughUnchanged() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("Hello, world!\n");
        f.flush();
        assertThat(captured()).isEqualTo("Hello, world!\n");
    }

    @Test
    void singleAndDoubleBackticksDoNotTriggerFence() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("a `b` c ``d`` e\n");
        f.flush();
        assertThat(captured()).isEqualTo("a `b` c ``d`` e\n");
    }

    @Test
    void fencedJavaBlockIsBufferedAndEmittedAtClose() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("Before\n```java\nint x = 42;\n```\nAfter\n");
        f.flush();
        String result = captured();
        // Fences are stripped in PLAIN mode and code body is emitted as-is
        assertThat(result).startsWith("Before\n");
        assertThat(result).contains("int x = 42;");
        assertThat(result).endsWith("After\n");
        assertThat(result).doesNotContain("```");
        assertThat(result).doesNotContain("java\n");  // language tag suppressed
    }

    @Test
    void fenceSplitAcrossChunks() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        // Split the opening fence across deltas
        f.appendText("text ``");
        f.appendText("`java\n");
        f.appendText("foo();\n```\nrest\n");
        f.flush();
        String result = captured();
        assertThat(result).startsWith("text ");
        assertThat(result).contains("foo();");
        assertThat(result).endsWith("rest\n");
        assertThat(result).doesNotContain("```");
    }

    @Test
    void closingFenceSplitAcrossChunks() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("```java\nfoo();\n``");
        f.appendText("`\nrest\n");
        f.flush();
        String result = captured();
        assertThat(result).contains("foo();");
        assertThat(result).endsWith("rest\n");
        assertThat(result).doesNotContain("```");
    }

    @Test
    void fenceWithNoLanguagePassesCodeThroughUnstyled() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("```\nplain code\n```\n");
        f.flush();
        assertThat(captured()).contains("plain code").doesNotContain("```");
    }

    @Test
    void unknownLanguageFallsBackToUnstyledPassthrough() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("```rust\nfn main() {}\n```\n");
        f.flush();
        assertThat(captured()).contains("fn main() {}").doesNotContain("```").doesNotContain("rust\n");
    }

    @Test
    void unclosedFenceIsFlushedAsRawAtFlush() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("```java\nfoo();\n");  // no closing fence
        f.flush();
        String result = captured();
        // Should contain the opening fence text + code, since we never saw close
        assertThat(result).contains("foo();");
    }

    @Test
    void coloredModeWrapsKeywordsWithAnsi() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.colored(), registry);
        f.appendText("```java\npublic class Foo {}\n```\n");
        f.flush();
        String result = captured();
        // ANSI escape (ESC = 0x1B = '') should appear, wrapping 'public' / 'class'
        assertThat(result).contains("public");
        assertThat(result).contains("class");
        assertThat(result.indexOf((char) 0x1b)).as("ANSI escape present").isGreaterThanOrEqualTo(0);
    }

    @Test
    void registryRegistrationEnablesNewLanguage() {
        registry.register("python", source -> {
            // trivial highlighter that returns one OTHER token
            return java.util.List.of(new io.jcc.cli.highlight.Token(
                io.jcc.cli.highlight.TokenType.OTHER, source));
        });
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("```python\nprint('hi')\n```\n");
        f.flush();
        assertThat(captured()).contains("print('hi')").doesNotContain("```");
    }

    @Test
    void multipleConsecutiveBlocks() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        f.appendText("First:\n```java\nint a;\n```\nSecond:\n```java\nint b;\n```\nDone\n");
        f.flush();
        String result = captured();
        assertThat(result).contains("First:").contains("Second:").contains("Done");
        assertThat(result).contains("int a;").contains("int b;");
        assertThat(result).doesNotContain("```");
    }

    @Test
    void streamCharByCharStillDetectsFences() {
        MarkdownTextFilter f = new MarkdownTextFilter(out, Style.PLAIN, registry);
        String src = "```java\nint x;\n```\n";
        for (int i = 0; i < src.length(); i++) {
            f.appendText(String.valueOf(src.charAt(i)));
        }
        f.flush();
        assertThat(captured()).contains("int x;").doesNotContain("```");
    }
}
