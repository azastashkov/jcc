package io.clawcode.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clawcode.core.JsonMapper;
import io.clawcode.core.Result;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.PermissionPolicy;
import io.clawcode.runtime.PermissionPrompter;
import io.clawcode.runtime.ToolContext;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BashToolTest {

    private final ObjectMapper mapper = JsonMapper.shared();

    @Test
    void runsSimpleCommand(@TempDir Path dir) throws Exception {
        BashTool tool = new BashTool();
        ToolContext ctx = context(dir);
        Result<ToolOutput, ToolError> result = tool.execute(
            mapper.readTree("{\"command\": \"echo hello\"}"), ctx);
        assertThat(result).isInstanceOf(Result.Ok.class);
        String text = ((Result.Ok<ToolOutput, ToolError>) result).value().asText();
        assertThat(text).contains("hello");
    }

    @Test
    void reportsNonZeroExit(@TempDir Path dir) throws Exception {
        BashTool tool = new BashTool();
        Result<ToolOutput, ToolError> result = tool.execute(
            mapper.readTree("{\"command\": \"exit 3\"}"), context(dir));
        assertThat(result).isInstanceOf(Result.Err.class);
        assertThat(((Result.Err<ToolOutput, ToolError>) result).error().message())
            .contains("exit code 3");
    }

    @Test
    void runsInWorkingDirectory(@TempDir Path dir) throws Exception {
        BashTool tool = new BashTool();
        Result<ToolOutput, ToolError> result = tool.execute(
            mapper.readTree("{\"command\": \"pwd\"}"), context(dir));
        assertThat(result).isInstanceOf(Result.Ok.class);
        assertThat(((Result.Ok<ToolOutput, ToolError>) result).value().asText())
            .contains(dir.toRealPath().toString());
    }

    @Test
    void missingCommandFails(@TempDir Path dir) throws Exception {
        BashTool tool = new BashTool();
        Result<ToolOutput, ToolError> result = tool.execute(
            mapper.readTree("{}"), context(dir));
        assertThat(result).isInstanceOf(Result.Err.class);
    }

    private ToolContext context(Path dir) {
        return new ToolContext(
            dir,
            new PermissionPolicy(PermissionMode.DANGER_FULL_ACCESS),
            PermissionPrompter.DENY_ALL,
            HttpClient.newHttpClient());
    }
}
