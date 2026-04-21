package io.jcc.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageRequest(
    String model,
    int maxTokens,
    List<InputMessage> messages,
    String system,
    List<ToolDefinition> tools,
    ToolChoice toolChoice,
    boolean stream,
    Double temperature,
    Double topP,
    Double frequencyPenalty,
    Double presencePenalty,
    List<String> stop,
    String reasoningEffort
) {

    public MessageRequest withStream(boolean streaming) {
        return new MessageRequest(
            model, maxTokens, messages, system, tools, toolChoice, streaming,
            temperature, topP, frequencyPenalty, presencePenalty, stop, reasoningEffort);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String model;
        private int maxTokens = 1024;
        private List<InputMessage> messages = List.of();
        private String system;
        private List<ToolDefinition> tools;
        private ToolChoice toolChoice;
        private boolean stream;
        private Double temperature;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private List<String> stop;
        private String reasoningEffort;

        public Builder model(String model) { this.model = model; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder messages(List<InputMessage> messages) { this.messages = messages; return this; }
        public Builder system(String system) { this.system = system; return this; }
        public Builder tools(List<ToolDefinition> tools) { this.tools = tools; return this; }
        public Builder toolChoice(ToolChoice toolChoice) { this.toolChoice = toolChoice; return this; }
        public Builder stream(boolean stream) { this.stream = stream; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder topP(Double topP) { this.topP = topP; return this; }
        public Builder stop(List<String> stop) { this.stop = stop; return this; }
        public Builder reasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; return this; }

        public MessageRequest build() {
            if (model == null) throw new IllegalStateException("model is required");
            return new MessageRequest(
                model, maxTokens, messages, system, tools, toolChoice, stream,
                temperature, topP, frequencyPenalty, presencePenalty, stop, reasoningEffort);
        }
    }
}
