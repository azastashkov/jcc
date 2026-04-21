package io.jcc.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jcc.core.JsonMapper;
import io.jcc.core.Result;
import io.jcc.runtime.PermissionMode;
import io.jcc.runtime.PermissionPolicy;
import io.jcc.runtime.PermissionPrompter;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolError;
import io.jcc.runtime.ToolOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileToolsTest {

    private final ObjectMapper mapper = JsonMapper.shared();

    @Test
    void readFileReturnsContents(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("hello.txt"), "one\ntwo\nthree\n");
        Result<ToolOutput, ToolError> result = new ReadFileTool().execute(
            mapper.readTree("{\"path\": \"hello.txt\"}"), context(dir));
        assertThat(asText(result)).isEqualTo("one\ntwo\nthree\n");
    }

    @Test
    void readFileHonorsOffsetAndLimit(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("hello.txt"), "a\nb\nc\nd\ne\n");
        Result<ToolOutput, ToolError> result = new ReadFileTool().execute(
            mapper.readTree("{\"path\": \"hello.txt\", \"offset\": 2, \"limit\": 2}"),
            context(dir));
        assertThat(asText(result)).isEqualTo("b\nc");
    }

    @Test
    void writeFileCreatesDirectories(@TempDir Path dir) throws Exception {
        Result<ToolOutput, ToolError> result = new WriteFileTool().execute(
            mapper.readTree("{\"path\": \"sub/new.txt\", \"content\": \"hi\"}"),
            context(dir));
        assertThat(result).isInstanceOf(Result.Ok.class);
        assertThat(Files.readString(dir.resolve("sub/new.txt"))).isEqualTo("hi");
    }

    @Test
    void editFileReplacesUniqueMatch(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("f.txt"), "alpha beta gamma");
        Result<ToolOutput, ToolError> result = new EditFileTool().execute(
            mapper.readTree("{\"path\": \"f.txt\", \"old_string\": \"beta\", \"new_string\": \"BETA\"}"),
            context(dir));
        assertThat(result).isInstanceOf(Result.Ok.class);
        assertThat(Files.readString(dir.resolve("f.txt"))).isEqualTo("alpha BETA gamma");
    }

    @Test
    void editFileRefusesAmbiguousMatch(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("f.txt"), "foo foo foo");
        Result<ToolOutput, ToolError> result = new EditFileTool().execute(
            mapper.readTree("{\"path\": \"f.txt\", \"old_string\": \"foo\", \"new_string\": \"bar\"}"),
            context(dir));
        assertThat(result).isInstanceOf(Result.Err.class);
    }

    @Test
    void editFileReplaceAllSucceeds(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("f.txt"), "foo foo foo");
        Result<ToolOutput, ToolError> result = new EditFileTool().execute(
            mapper.readTree("{\"path\": \"f.txt\", \"old_string\": \"foo\", \"new_string\": \"bar\", \"replace_all\": true}"),
            context(dir));
        assertThat(result).isInstanceOf(Result.Ok.class);
        assertThat(Files.readString(dir.resolve("f.txt"))).isEqualTo("bar bar bar");
    }

    @Test
    void globFindsFiles(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("a.java"), "");
        Files.writeString(dir.resolve("sub/b.java"), "");
        Files.writeString(dir.resolve("sub/c.txt"), "");
        Result<ToolOutput, ToolError> result = new GlobTool().execute(
            mapper.readTree("{\"pattern\": \"**/*.java\"}"), context(dir));
        String out = asText(result);
        assertThat(out).contains("a.java").contains("b.java").doesNotContain("c.txt");
    }

    @Test
    void grepFindsMatches(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "hello world\ngoodbye");
        Files.writeString(dir.resolve("b.txt"), "Hello Moon");
        Result<ToolOutput, ToolError> result = new GrepTool().execute(
            mapper.readTree("{\"pattern\": \"hello\", \"case_insensitive\": true}"),
            context(dir));
        String out = asText(result);
        assertThat(out).contains("a.txt:1").contains("b.txt:1");
    }

    @Test
    void grepReturnsNoMatches(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "nothing here");
        Result<ToolOutput, ToolError> result = new GrepTool().execute(
            mapper.readTree("{\"pattern\": \"zzz\"}"), context(dir));
        assertThat(asText(result)).contains("No matches");
    }

    private static String asText(Result<ToolOutput, ToolError> result) {
        if (result instanceof Result.Ok<ToolOutput, ToolError> ok) {
            return ok.value().asText();
        }
        throw new AssertionError("expected Ok but was " + result);
    }

    private static ToolContext context(Path dir) {
        return new ToolContext(
            dir,
            new PermissionPolicy(PermissionMode.DANGER_FULL_ACCESS),
            PermissionPrompter.DENY_ALL,
            HttpClient.newHttpClient());
    }
}
