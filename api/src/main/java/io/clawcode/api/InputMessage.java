package io.clawcode.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.clawcode.core.ContentBlock;

import java.util.List;

public record InputMessage(String role, List<ContentBlock> content) {

    @JsonCreator
    public InputMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") List<ContentBlock> content) {
        this.role = role;
        this.content = List.copyOf(content);
    }

    public static InputMessage userText(String text) {
        return new InputMessage("user", List.of(new ContentBlock.Text(text)));
    }

    public static InputMessage assistantText(String text) {
        return new InputMessage("assistant", List.of(new ContentBlock.Text(text)));
    }
}
