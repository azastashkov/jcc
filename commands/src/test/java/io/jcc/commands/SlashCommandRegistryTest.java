package io.jcc.commands;

import io.jcc.core.Usage;
import io.jcc.runtime.PermissionsConfig;
import io.jcc.runtime.RuntimeConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandRegistryTest {

    @Test
    void helpListsCommands() {
        SlashCommandRegistry registry = SlashCommandRegistry.defaults();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        registry.dispatch("/help", ctx(new PrintStream(out, true, StandardCharsets.UTF_8)));
        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("/help").contains("/status").contains("/cost").contains("/clear");
    }

    @Test
    void unknownCommandReturnsUnknownResult() {
        SlashCommandRegistry registry = SlashCommandRegistry.defaults();
        SlashCommandResult result = registry.dispatch("/nope", ctx(new PrintStream(OutputStream())));
        assertThat(result).isInstanceOf(SlashCommandResult.Unknown.class);
    }

    @Test
    void exitReturnsExit() {
        SlashCommandRegistry registry = SlashCommandRegistry.defaults();
        SlashCommandResult result = registry.dispatch("/exit", ctx(new PrintStream(OutputStream())));
        assertThat(result).isInstanceOf(SlashCommandResult.Exit.class);
    }

    @Test
    void clearInvokesClearHistory() {
        AtomicInteger counter = new AtomicInteger();
        SlashCommandRegistry registry = SlashCommandRegistry.defaults();
        SlashContext ctx = new SlashContext(
            null, RuntimeConfig.empty(), null,
            () -> Usage.EMPTY,
            counter::incrementAndGet,
            new PrintStream(OutputStream()));
        registry.dispatch("/clear", ctx);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void costPrintsUsage() {
        SlashCommandRegistry registry = SlashCommandRegistry.defaults();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SlashContext ctx = new SlashContext(
            null, RuntimeConfig.empty(), null,
            () -> new Usage(100, 10, 20, 50),
            () -> {},
            new PrintStream(out, true, StandardCharsets.UTF_8));
        registry.dispatch("/cost", ctx);
        assertThat(out.toString(StandardCharsets.UTF_8))
            .contains("input=100").contains("output=50").contains("total=180");
    }

    private SlashContext ctx(PrintStream out) {
        return new SlashContext(
            null,
            new RuntimeConfig("claude-opus-4-7", 1024, PermissionsConfig.empty()),
            null,
            () -> Usage.EMPTY,
            () -> {},
            out);
    }

    private static java.io.OutputStream OutputStream() {
        return java.io.OutputStream.nullOutputStream();
    }
}
