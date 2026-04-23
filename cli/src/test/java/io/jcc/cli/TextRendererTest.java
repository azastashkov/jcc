package io.jcc.cli;

import io.jcc.core.Usage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextRendererTest {

    @Test
    void toolUseAndResultAreVisible() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.ToolUseRequested(
                "tu_1", "write_file", "{\"path\":\"/tmp/x\",\"content\":\"hi\"}"));
            r.onEvent(new AssistantEvent.ToolResult(
                "tu_1", "write_file", "Wrote 2 chars to /tmp/x", false));
            r.onEvent(new AssistantEvent.UsageReport(new Usage(0, 0, 0, 5)));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }

        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("write_file");
        assertThat(out).contains("/tmp/x");
        assertThat(out).contains("Wrote 2 chars to /tmp/x");
        assertThat(out).contains("[tokens in=0 out=5");
    }

    @Test
    void toolResultErrorIsMarkedDistinctly() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.ToolUseRequested("tu_1", "bash", "{\"command\":\"false\"}"));
            r.onEvent(new AssistantEvent.ToolResult("tu_1", "bash", "exit 1: command failed", true));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }

        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("bash");
        assertThat(out).contains("exit 1: command failed");
        assertThat(out.toLowerCase()).contains("error");
    }

    @Test
    void nonEndTurnStopReasonIsVisible() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.TextDelta("Now I'll write the test."));
            r.onEvent(new AssistantEvent.UsageReport(new Usage(0, 0, 0, 1024)));
            r.onEvent(new AssistantEvent.TurnFinish("max_tokens"));
        }

        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("max_tokens");
    }

    @Test
    void endTurnStopReasonIsSilent() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.TextDelta("All done."));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }

        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).doesNotContain("end_turn");
        assertThat(out).doesNotContain("stop:");
    }

    @Test
    void usageReportPrintsCumulativeSnapshot() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.UsageReport(new Usage(100, 0, 0, 50)));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("sent=100").contains("recv=50");
    }

    @Test
    void duplicateUsageReportsAreSuppressed() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.UsageReport(new Usage(50, 0, 0, 25)));
            r.onEvent(new AssistantEvent.UsageReport(new Usage(50, 0, 0, 25)));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        int firstIdx = out.indexOf("sent=50 recv=25");
        assertThat(firstIdx).isGreaterThanOrEqualTo(0);
        int secondIdx = out.indexOf("sent=50 recv=25", firstIdx + 1);
        assertThat(secondIdx).as("duplicate snapshot should be suppressed").isEqualTo(-1);
    }

    @Test
    void sentIncludesCacheTokens() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.UsageReport(new Usage(10, 100, 1000, 5)));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("sent=1110").contains("recv=5");
    }

    @Test
    void zeroUsageReportIsSuppressed() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.UsageReport(Usage.EMPTY));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).doesNotContain("sent=0");
    }

    @Test
    void contextPercentShownInSnapshotWhenContextWindowSet() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)
            .setContextWindow(100_000)) {
            r.onEvent(new AssistantEvent.UsageReport(new Usage(15_000, 0, 0, 200)));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("sent=15000 recv=200 ctx=15%");
        assertThat(out).contains("ctx=15% (15K/100K)");
    }

    @Test
    void contextPercentAbsentWhenContextWindowZero() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.UsageReport(new Usage(15_000, 0, 0, 200)));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("sent=15000 recv=200");
        assertThat(out).doesNotContain("ctx=");
    }

    @Test
    void contextPercentUsesDeltaNotCumulativeAcrossSubTurns() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)
            .setContextWindow(100_000)) {
            // Sub-turn 1: cumulative input=10000
            r.onEvent(new AssistantEvent.UsageReport(new Usage(10_000, 0, 0, 100)));
            // Sub-turn 2: cumulative input=25000 (i.e. this sub-turn sent 15000)
            r.onEvent(new AssistantEvent.UsageReport(new Usage(25_000, 0, 0, 300)));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        // First snapshot: 10K/100K = 10%
        assertThat(out).contains("sent=10000 recv=100 ctx=10%");
        // Second snapshot: latest sub-turn delta was 15K → 15%
        assertThat(out).contains("sent=25000 recv=300 ctx=15%");
        // Footer reflects latest sub-turn's 15K
        assertThat(out).contains("ctx=15% (15K/100K)");
    }

    @Test
    void abbreviateTokensFormatsKAndM() {
        assertThat(TextRenderer.abbreviateTokens(42)).isEqualTo("42");
        assertThat(TextRenderer.abbreviateTokens(1_000)).isEqualTo("1K");
        assertThat(TextRenderer.abbreviateTokens(15_000)).isEqualTo("15K");
        assertThat(TextRenderer.abbreviateTokens(15_500)).isEqualTo("15.5K");
        assertThat(TextRenderer.abbreviateTokens(1_000_000)).isEqualTo("1M");
        assertThat(TextRenderer.abbreviateTokens(1_500_000)).isEqualTo("1.5M");
    }

    @Test
    void textDeltaStillStreamsInline() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8), Style.PLAIN)) {
            r.onEvent(new AssistantEvent.TextDelta("Hello, "));
            r.onEvent(new AssistantEvent.TextDelta("world!"));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }

        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("Hello, world!");
    }
}
