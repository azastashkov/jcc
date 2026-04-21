package io.clawcode.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.clawcode.core.ContentBlock;
import io.clawcode.core.MessageRole;
import io.clawcode.core.Usage;

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
