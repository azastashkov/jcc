package io.clawcode.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ToolChoice.Auto.class, name = "auto"),
    @JsonSubTypes.Type(value = ToolChoice.Any.class, name = "any"),
    @JsonSubTypes.Type(value = ToolChoice.Tool.class, name = "tool")
})
public sealed interface ToolChoice {

    @JsonTypeName("auto")
    record Auto() implements ToolChoice {
        public static final Auto INSTANCE = new Auto();
    }

    @JsonTypeName("any")
    record Any() implements ToolChoice {
        public static final Any INSTANCE = new Any();
    }

    @JsonTypeName("tool")
    record Tool(String name) implements ToolChoice {}
}
