package io.clawcode.runtime.subagent;

import com.fasterxml.jackson.databind.JsonNode;
import io.clawcode.api.ProviderClient;
import io.clawcode.api.StreamEvent;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.JsonMapper;
import io.clawcode.core.Result;
import io.clawcode.core.Usage;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.PermissionPolicy;
import io.clawcode.runtime.PermissionPrompter;
import io.clawcode.runtime.ToolContext;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolExecutor;
import io.clawcode.runtime.ToolOutput;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentExecutorTest {

    @Test
    void subagentReceivesOnlyAllowedToolsAndReturnsFinalText() {
        List<List<ToolDefinition>> toolsSeenByProvider = new ArrayList<>();
        ProviderClient provider = (req, handler) -> {
            toolsSeenByProvider.add(req.tools());
            handler.onEvent(new StreamEvent.MessageStart(
                new io.clawcode.api.MessageResponse(
                    "msg_1", "message", "assistant", List.of(),
                    "claude-opus-4-7", null, null, new Usage(5, 0, 0, 0), null)));
            handler.onEvent(new StreamEvent.ContentBlockStart(0,
                new io.clawcode.core.ContentBlock.Text("")));
            handler.onEvent(new StreamEvent.ContentBlockDeltaEvent(0,
                new io.clawcode.api.ContentBlockDelta.TextDelta("found 3 matches")));
            handler.onEvent(new StreamEvent.ContentBlockStop(0));
            handler.onEvent(new StreamEvent.MessageDeltaEvent(
                new StreamEvent.MessageDelta("end_turn", null), new Usage(0, 0, 0, 3)));
            handler.onEvent(StreamEvent.MessageStop.INSTANCE);
        };

        List<ToolDefinition> parentTools = List.of(
            new ToolDefinition("bash", "run shell", JsonMapper.shared().createObjectNode()),
            new ToolDefinition("read_file", "read", JsonMapper.shared().createObjectNode()),
            new ToolDefinition("glob", "glob", JsonMapper.shared().createObjectNode()),
            new ToolDefinition("grep", "grep", JsonMapper.shared().createObjectNode()),
            new ToolDefinition("web_fetch", "fetch", JsonMapper.shared().createObjectNode()),
            new ToolDefinition("web_search", "search", JsonMapper.shared().createObjectNode()),
            new ToolDefinition("write_file", "write", JsonMapper.shared().createObjectNode()));

        ToolExecutor parentExec = new ToolExecutor() {
            @Override public List<ToolDefinition> toolDefinitions() { return parentTools; }
            @Override public Result<ToolOutput, ToolError> execute(String name, JsonNode input, ToolContext ctx) {
                return Result.ok(new ToolOutput.Text("stub"));
            }
            @Override public PermissionMode requiredMode(String name) { return PermissionMode.READ_ONLY; }
        };

        TaskRegistry registry = new TaskRegistry();
        SubagentExecutor executor = new SubagentExecutor(
            provider, parentExec,
            new PermissionPolicy(PermissionMode.DANGER_FULL_ACCESS),
            PermissionPrompter.DENY_ALL,
            new ToolContext(Path.of("."),
                new PermissionPolicy(PermissionMode.DANGER_FULL_ACCESS),
                PermissionPrompter.DENY_ALL, HttpClient.newHttpClient()),
            "claude-opus-4-7", 1024, registry);

        SubagentResult result = executor.run("explore repo", "list matches", "Explore", null);

        assertThat(result.text()).isEqualTo("found 3 matches");
        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);

        assertThat(toolsSeenByProvider).hasSize(1);
        List<String> names = toolsSeenByProvider.get(0).stream().map(ToolDefinition::name).toList();
        assertThat(names).containsExactlyInAnyOrder(
            "read_file", "glob", "grep", "web_fetch", "web_search");
        assertThat(names).doesNotContain("bash", "write_file");

        assertThat(registry.all()).hasSize(1);
        assertThat(registry.all().iterator().next().status()).isEqualTo(TaskStatus.COMPLETED);
    }
}
