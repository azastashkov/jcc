package io.clawcode.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.clawcode.core.ContentBlock;
import io.clawcode.core.Usage;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StreamEvent.MessageStart.class, name = "message_start"),
    @JsonSubTypes.Type(value = StreamEvent.ContentBlockStart.class, name = "content_block_start"),
    @JsonSubTypes.Type(value = StreamEvent.ContentBlockDeltaEvent.class, name = "content_block_delta"),
    @JsonSubTypes.Type(value = StreamEvent.ContentBlockStop.class, name = "content_block_stop"),
    @JsonSubTypes.Type(value = StreamEvent.MessageDeltaEvent.class, name = "message_delta"),
    @JsonSubTypes.Type(value = StreamEvent.MessageStop.class, name = "message_stop"),
    @JsonSubTypes.Type(value = StreamEvent.Ping.class, name = "ping"),
    @JsonSubTypes.Type(value = StreamEvent.ErrorEvent.class, name = "error")
})
public sealed interface StreamEvent {

    @JsonTypeName("message_start")
    record MessageStart(MessageResponse message) implements StreamEvent {}

    @JsonTypeName("content_block_start")
    record ContentBlockStart(int index, ContentBlock contentBlock) implements StreamEvent {}

    @JsonTypeName("content_block_delta")
    record ContentBlockDeltaEvent(int index, ContentBlockDelta delta) implements StreamEvent {}

    @JsonTypeName("content_block_stop")
    record ContentBlockStop(int index) implements StreamEvent {}

    @JsonTypeName("message_delta")
    record MessageDeltaEvent(MessageDelta delta, Usage usage) implements StreamEvent {}

    @JsonTypeName("message_stop")
    record MessageStop() implements StreamEvent {
        public static final MessageStop INSTANCE = new MessageStop();
    }

    @JsonTypeName("ping")
    record Ping() implements StreamEvent {
        public static final Ping INSTANCE = new Ping();
    }

    @JsonTypeName("error")
    record ErrorEvent(ErrorPayload error) implements StreamEvent {}

    record MessageDelta(String stopReason, String stopSequence) {}

    record ErrorPayload(String type, String message) {}
}
