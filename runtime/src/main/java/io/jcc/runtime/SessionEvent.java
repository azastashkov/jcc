package io.jcc.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionEvent.Meta.class, name = "session_meta"),
    @JsonSubTypes.Type(value = SessionEvent.Message.class, name = "message")
})
public sealed interface SessionEvent {

    @JsonTypeName("session_meta")
    record Meta(
        String sessionId,
        long createdAtMs,
        long updatedAtMs,
        int version,
        String workspaceRoot,
        String model
    ) implements SessionEvent {}

    @JsonTypeName("message")
    record Message(ConversationMessage message) implements SessionEvent {}
}
