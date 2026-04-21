package io.jcc.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.jcc.core.ContentBlock;
import io.jcc.core.MessageRole;
import io.jcc.core.Usage;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationMessage(MessageRole role, List<ContentBlock> blocks, Usage usage) {

    public static ConversationMessage user(List<ContentBlock> blocks) {
        return new ConversationMessage(MessageRole.USER, blocks, null);
    }

    public static ConversationMessage assistant(List<ContentBlock> blocks, Usage usage) {
        return new ConversationMessage(MessageRole.ASSISTANT, blocks, usage);
    }
}
