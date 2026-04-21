package io.jcc.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBlockTest {

    private final ObjectMapper mapper = JsonMapper.build();

    @Test
    void roundTripsTextBlock() throws Exception {
        ContentBlock block = new ContentBlock.Text("hello");
        String json = mapper.writeValueAsString(block);
        assertThat(json).contains("\"type\":\"text\"").contains("\"text\":\"hello\"");
        ContentBlock parsed = mapper.readValue(json, ContentBlock.class);
        assertThat(parsed).isEqualTo(block);
    }

    @Test
    void roundTripsToolUseBlock() throws Exception {
        String json = """
            { "type": "tool_use", "id": "tu_1", "name": "bash", "input": {"command": "ls"} }
            """;
        ContentBlock parsed = mapper.readValue(json, ContentBlock.class);
        assertThat(parsed).isInstanceOf(ContentBlock.ToolUse.class);
        ContentBlock.ToolUse use = (ContentBlock.ToolUse) parsed;
        assertThat(use.id()).isEqualTo("tu_1");
        assertThat(use.name()).isEqualTo("bash");
        assertThat(use.input().get("command").asText()).isEqualTo("ls");
    }

    @Test
    void parsesThinkingBlock() throws Exception {
        String json = """
            { "type": "thinking", "thinking": "let me think...", "signature": "sig-abc" }
            """;
        ContentBlock parsed = mapper.readValue(json, ContentBlock.class);
        assertThat(parsed).isEqualTo(new ContentBlock.Thinking("let me think...", "sig-abc"));
    }

    @Test
    void parsesRedactedThinkingBlock() throws Exception {
        String json = """
            { "type": "redacted_thinking", "data": {"encrypted": "xyz"} }
            """;
        ContentBlock parsed = mapper.readValue(json, ContentBlock.class);
        assertThat(parsed).isInstanceOf(ContentBlock.RedactedThinking.class);
    }
}
