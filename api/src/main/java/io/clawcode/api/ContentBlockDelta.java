package io.clawcode.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ContentBlockDelta.TextDelta.class, name = "text_delta"),
    @JsonSubTypes.Type(value = ContentBlockDelta.InputJsonDelta.class, name = "input_json_delta"),
    @JsonSubTypes.Type(value = ContentBlockDelta.ThinkingDelta.class, name = "thinking_delta"),
    @JsonSubTypes.Type(value = ContentBlockDelta.SignatureDelta.class, name = "signature_delta")
})
public sealed interface ContentBlockDelta {

    @JsonTypeName("text_delta")
    record TextDelta(String text) implements ContentBlockDelta {}

    @JsonTypeName("input_json_delta")
    record InputJsonDelta(String partialJson) implements ContentBlockDelta {}

    @JsonTypeName("thinking_delta")
    record ThinkingDelta(String thinking) implements ContentBlockDelta {}

    @JsonTypeName("signature_delta")
    record SignatureDelta(String signature) implements ContentBlockDelta {}
}
