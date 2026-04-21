package io.jcc.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.api.MessageRequest;
import io.jcc.api.ProviderClient;
import io.jcc.api.StreamEvent;
import io.jcc.api.StreamEventHandler;
import io.jcc.api.ToolDefinition;
import io.jcc.core.ContentBlock;
import io.jcc.core.JsonMapper;
import io.jcc.core.Result;
import io.jcc.core.Usage;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuntimeTest {

    @Test
    void runsToolLoopAndTerminates() throws Exception {
        List<StreamEvent> turn1 = List.of(
            start("msg_1", 10),
            textBlockStart(0),
            textDelta(0, "Let me check. "),
            blockStop(0),
            toolUseStart(1, "tu_1", "read_file"),
            inputJsonDelta(1, "{\"path\":\"a.txt\"}"),
            blockStop(1),
            messageDelta("tool_use", 6),
            messageStop());

        List<StreamEvent> turn2 = List.of(
            start("msg_2", 30),
            textBlockStart(0),
            textDelta(0, "File has 3 lines."),
            blockStop(0),
            messageDelta("end_turn", 4),
            messageStop());

        Deque<List<StreamEvent>> turns = new ArrayDeque<>(List.of(turn1, turn2));

        ProviderClient fake = (request, handler) -> {
            for (StreamEvent e : turns.pop()) handler.onEvent(e);
        };

        StubToolExecutor executor = new StubToolExecutor();
        ToolContext ctx = new ToolContext(
            Path.of("."),
            new PermissionPolicy(PermissionMode.DANGER_FULL_ACCESS),
            PermissionPrompter.DENY_ALL,
            HttpClient.newHttpClient());

        ConversationRuntime rt = new ConversationRuntime(
            fake, executor,
            new PermissionPolicy(PermissionMode.DANGER_FULL_ACCESS),
            PermissionPrompter.DENY_ALL,
            ctx, "claude-opus-4-7", 1024, null);

        RecordingHandler handler = new RecordingHandler();
        TurnSummary summary = rt.runTurn("What does a.txt say?", handler);

        assertThat(summary.finalText()).isEqualTo("File has 3 lines.");
        assertThat(summary.turns()).isEqualTo(2);
        assertThat(summary.stopReason()).isEqualTo("end_turn");
        assertThat(executor.callsReceived).containsExactly("read_file");
        assertThat(handler.toolResults).hasSize(1);
        assertThat(handler.toolResults.get(0)).isEqualTo("tu_1:read_file:ok");
    }

    @Test
    void permissionDenialPropagatesAsErrorToolResult() throws Exception {
        List<StreamEvent> turn1 = List.of(
            start("msg_1", 10),
            toolUseStart(0, "tu_1", "bash"),
            inputJsonDelta(0, "{\"command\":\"rm -rf /\"}"),
            blockStop(0),
            messageDelta("tool_use", 2),
            messageStop());

        List<StreamEvent> turn2 = List.of(
            start("msg_2", 12),
            textBlockStart(0),
            textDelta(0, "Cannot do that."),
            blockStop(0),
            messageDelta("end_turn", 4),
            messageStop());

        Deque<List<StreamEvent>> turns = new ArrayDeque<>(List.of(turn1, turn2));
        ProviderClient fake = (req, handler) -> turns.pop().forEach(handler::onEvent);

        StubToolExecutor executor = new StubToolExecutor();
        PermissionPolicy readOnly = new PermissionPolicy(PermissionMode.READ_ONLY)
            .withToolRequirement("bash", PermissionMode.WORKSPACE_WRITE);

        ConversationRuntime rt = new ConversationRuntime(
            fake, executor, readOnly, PermissionPrompter.DENY_ALL,
            new ToolContext(Path.of("."), readOnly, PermissionPrompter.DENY_ALL, HttpClient.newHttpClient()),
            "claude-opus-4-7", 1024, null);

        RecordingHandler handler = new RecordingHandler();
        TurnSummary summary = rt.runTurn("clean up", handler);

        assertThat(summary.finalText()).isEqualTo("Cannot do that.");
        assertThat(executor.callsReceived).isEmpty();
        assertThat(handler.toolResults).hasSize(1);
        assertThat(handler.toolResults.get(0)).startsWith("tu_1:bash:err:");
    }

    private static StreamEvent start(String id, int inputTokens) {
        io.jcc.api.MessageResponse msg = new io.jcc.api.MessageResponse(
            id, "message", "assistant", List.of(), "claude-opus-4-7",
            null, null, new Usage(inputTokens, 0, 0, 0), null);
        return new StreamEvent.MessageStart(msg);
    }

    private static StreamEvent textBlockStart(int index) {
        return new StreamEvent.ContentBlockStart(index, new ContentBlock.Text(""));
    }

    private static StreamEvent textDelta(int index, String text) {
        return new StreamEvent.ContentBlockDeltaEvent(index,
            new io.jcc.api.ContentBlockDelta.TextDelta(text));
    }

    private static StreamEvent toolUseStart(int index, String id, String name) {
        return new StreamEvent.ContentBlockStart(index,
            new ContentBlock.ToolUse(id, name, JsonMapper.shared().createObjectNode()));
    }

    private static StreamEvent inputJsonDelta(int index, String json) {
        return new StreamEvent.ContentBlockDeltaEvent(index,
            new io.jcc.api.ContentBlockDelta.InputJsonDelta(json));
    }

    private static StreamEvent blockStop(int index) {
        return new StreamEvent.ContentBlockStop(index);
    }

    private static StreamEvent messageDelta(String stopReason, int outputTokens) {
        return new StreamEvent.MessageDeltaEvent(
            new StreamEvent.MessageDelta(stopReason, null),
            new Usage(0, 0, 0, outputTokens));
    }

    private static StreamEvent messageStop() {
        return StreamEvent.MessageStop.INSTANCE;
    }

    private static final class StubToolExecutor implements ToolExecutor {
        final List<String> callsReceived = new ArrayList<>();

        @Override
        public List<ToolDefinition> toolDefinitions() {
            return List.of();
        }

        @Override
        public Result<ToolOutput, ToolError> execute(String name, JsonNode input, ToolContext ctx) {
            callsReceived.add(name);
            return Result.ok(new ToolOutput.Text("[ok] " + name));
        }

        @Override
        public PermissionMode requiredMode(String name) {
            return PermissionMode.READ_ONLY;
        }
    }

    private static final class RecordingHandler implements AssistantEventHandler {
        final List<String> toolResults = new ArrayList<>();

        @Override
        public void onToolResult(String id, String name, String output, boolean isError) {
            toolResults.add(id + ":" + name + ":" + (isError ? "err:" + output : "ok"));
        }
    }
}
