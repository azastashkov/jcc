package io.jcc.cli;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StyleTest {

    private static final char ESC = 0x1b;

    @Test
    void plainIsIdentityForEverything() {
        Style s = Style.PLAIN;
        assertThat(s.toolMarker("●")).isEqualTo("●");
        assertThat(s.toolName("write_file")).isEqualTo("write_file");
        assertThat(s.toolArgs("{\"path\":\"/x\"}")).isEqualTo("{\"path\":\"/x\"}");
        assertThat(s.toolResult("→ ok")).isEqualTo("→ ok");
        assertThat(s.toolError("error")).isEqualTo("error");
        assertThat(s.progress("· sent=1 recv=2")).isEqualTo("· sent=1 recv=2");
        assertThat(s.stopReason("[stop: max_tokens]")).isEqualTo("[stop: max_tokens]");
        assertThat(s.tokenFooter("[tokens ...]")).isEqualTo("[tokens ...]");
        assertThat(s.dim("x")).isEqualTo("x");
        assertThat(s.bold("x")).isEqualTo("x");
    }

    @Test
    void coloredEmitsAnsiEscapesAroundContent() {
        Style s = Style.colored();
        String wrapped = s.toolName("write_file");
        assertThat(wrapped)
            .contains("write_file")
            .startsWith(String.valueOf(ESC));
        assertThat(wrapped.length()).isGreaterThan("write_file".length());
    }

    @Test
    void detectReturnsPlainWhenNoColorEnvIsSet() {
        Style s = Style.detect(name -> "NO_COLOR".equals(name) ? "1" : null);
        assertThat(s.isColor()).isFalse();
    }

    @Test
    void detectReturnsColoredWhenForceColorIsSet() {
        Style s = Style.detect(name -> "FORCE_COLOR".equals(name) ? "1" : null);
        assertThat(s.isColor()).isTrue();
    }

    @Test
    void detectReturnsPlainForDumbTerminal() {
        Map<String, String> env = Map.of("TERM", "dumb");
        Style s = Style.detect(env::get);
        assertThat(s.isColor()).isFalse();
    }

    @Test
    void noColorEnvWinsOverForceColor() {
        Map<String, String> env = Map.of("NO_COLOR", "1", "FORCE_COLOR", "1");
        Style s = Style.detect(env::get);
        assertThat(s.isColor()).isFalse();
    }

    @Test
    void coloredEmptyStringStaysEmpty() {
        Style s = Style.colored();
        assertThat(s.toolName("")).isEmpty();
    }
}
