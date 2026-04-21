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
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8))) {
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
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8))) {
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
    void textDeltaStillStreamsInline() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TextRenderer r = new TextRenderer(new PrintStream(buf, true, StandardCharsets.UTF_8))) {
            r.onEvent(new AssistantEvent.TextDelta("Hello, "));
            r.onEvent(new AssistantEvent.TextDelta("world!"));
            r.onEvent(new AssistantEvent.TurnFinish("end_turn"));
        }

        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("Hello, world!");
    }
}
