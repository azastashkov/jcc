package io.jcc.api;

import io.jcc.core.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseLineParserTest {

    private final List<StreamEvent> events = new ArrayList<>();
    private final SseLineParser parser = new SseLineParser(JsonMapper.shared(), events::add);

    @Test
    void parsesAnthropicStreamFixture() {
        String[] lines = ("""
            event: message_start
            data: {"type": "message_start", "message": {"id": "msg_1", "type": "message", "role": "assistant", "content": [], "model": "claude-opus-4-6", "stop_reason": null, "stop_sequence": null, "usage": {"input_tokens": 10, "output_tokens": 0}}}

            event: content_block_start
            data: {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}}

            event: content_block_delta
            data: {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": "Hello"}}

            event: content_block_delta
            data: {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": " world"}}

            event: content_block_stop
            data: {"type": "content_block_stop", "index": 0}

            event: message_delta
            data: {"type": "message_delta", "delta": {"stop_reason": "end_turn", "stop_sequence": null}, "usage": {"output_tokens": 2}}

            event: message_stop
            data: {"type": "message_stop"}
            """).split("\n", -1);

        for (String line : lines) {
            parser.pushLine(line);
        }
        parser.close();

        assertThat(events).hasSize(7);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.MessageStart.class);
        assertThat(events.get(1)).isInstanceOf(StreamEvent.ContentBlockStart.class);
        assertThat(events.get(2)).isInstanceOf(StreamEvent.ContentBlockDeltaEvent.class);
        assertThat(events.get(3)).isInstanceOf(StreamEvent.ContentBlockDeltaEvent.class);
        assertThat(events.get(4)).isInstanceOf(StreamEvent.ContentBlockStop.class);
        assertThat(events.get(5)).isInstanceOf(StreamEvent.MessageDeltaEvent.class);
        assertThat(events.get(6)).isInstanceOf(StreamEvent.MessageStop.class);

        StreamEvent.ContentBlockDeltaEvent first = (StreamEvent.ContentBlockDeltaEvent) events.get(2);
        assertThat(first.delta()).isEqualTo(new ContentBlockDelta.TextDelta("Hello"));

        StreamEvent.MessageStart start = (StreamEvent.MessageStart) events.get(0);
        assertThat(start.message().model()).isEqualTo("claude-opus-4-6");
        assertThat(start.message().usage().inputTokens()).isEqualTo(10);
    }

    @Test
    void ignoresCommentLinesAndKeepAlives() {
        parser.pushLine(": this is a comment");
        parser.pushLine(": another");
        parser.pushLine("");
        assertThat(events).isEmpty();
    }

    @Test
    void handlesMultilineDataField() {
        parser.pushLine("data: {\"type\": \"ping\"}");
        parser.pushLine("");
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.Ping.class);
    }

    @Test
    void handlesPingEvent() {
        parser.pushLine("event: ping");
        parser.pushLine("data: {\"type\": \"ping\"}");
        parser.pushLine("");
        assertThat(events).containsExactly(StreamEvent.Ping.INSTANCE);
    }

    @Test
    void parsesErrorEvent() {
        parser.pushLine("event: error");
        parser.pushLine("data: {\"type\": \"error\", \"error\": {\"type\": \"overloaded\", \"message\": \"Try later\"}}");
        parser.pushLine("");
        assertThat(events).hasSize(1);
        StreamEvent.ErrorEvent err = (StreamEvent.ErrorEvent) events.get(0);
        assertThat(err.error().type()).isEqualTo("overloaded");
        assertThat(err.error().message()).isEqualTo("Try later");
    }

    @Test
    void throwsOnMalformedJson() {
        parser.pushLine("data: not-valid-json");
        assertThatThrownBy(() -> parser.pushLine(""))
            .isInstanceOf(ProviderException.class)
            .hasMessageContaining("Malformed SSE frame");
    }
}
