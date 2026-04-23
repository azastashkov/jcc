package io.jcc.cli;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ModelContextWindowsTest {

    @Test
    void claudeOpus4_7Is1M() {
        assertThat(ModelContextWindows.get("claude-opus-4-7"))
            .isEqualTo(OptionalInt.of(1_000_000));
    }

    @Test
    void claudeSonnetAndHaiku4Are200k() {
        assertThat(ModelContextWindows.get("claude-sonnet-4-6"))
            .isEqualTo(OptionalInt.of(200_000));
        assertThat(ModelContextWindows.get("claude-haiku-4-5"))
            .isEqualTo(OptionalInt.of(200_000));
    }

    @Test
    void gpt4oIs128k() {
        assertThat(ModelContextWindows.get("gpt-4o"))
            .isEqualTo(OptionalInt.of(128_000));
    }

    @Test
    void unknownModelIsEmpty() {
        assertThat(ModelContextWindows.get("custom-local-model"))
            .isEqualTo(OptionalInt.empty());
    }

    @Test
    void nullAndBlankAreEmpty() {
        assertThat(ModelContextWindows.get(null)).isEqualTo(OptionalInt.empty());
        assertThat(ModelContextWindows.get("")).isEqualTo(OptionalInt.empty());
        assertThat(ModelContextWindows.get("  ")).isEqualTo(OptionalInt.empty());
    }
}
