package io.jcc.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.jcc.commands.SlashCommandRegistry;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;
import io.jcc.core.Usage;
import io.jcc.runtime.AssistantEventHandler;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

public final class ReplSession {
    private static final Logger log = LoggerFactory.getLogger(ReplSession.class);

    private final RuntimeEnvironment env;
    private final SlashCommandRegistry slashRegistry;
    private final PrintStream out;
    private final Style style;
    private Usage runningUsage = Usage.EMPTY;

    public ReplSession(RuntimeEnvironment env, SlashCommandRegistry registry, PrintStream out) {
        this(env, registry, out, Style.detect());
    }

    public ReplSession(RuntimeEnvironment env, SlashCommandRegistry registry, PrintStream out, Style style) {
        this.env = env;
        this.slashRegistry = registry;
        this.out = out;
        this.style = style;
    }

    public int run() {
        try (Terminal terminal = TerminalBuilder.builder()
            .system(true)
            .dumb(true)
            .build()) {

            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("jcc")
                .variable(LineReader.HISTORY_FILE, historyPath().toString())
                .completer(new StringsCompleter(slashRegistry.names().stream()
                    .map(n -> "/" + n)
                    .toList()))
                .build();

            out.println(style.dim("jcc REPL — type /help for commands, /exit to quit."));
            out.println(style.dim("Session: " + env.session.sessionId() + "  model=" + env.model));
            out.println();

            while (true) {
                String line;
                try {
                    line = reader.readLine("jcc> ");
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }
                if (line == null || line.isBlank()) continue;

                if (line.trim().startsWith("/")) {
                    if (dispatchSlash(line) instanceof SlashCommandResult.Exit) {
                        break;
                    }
                } else {
                    runPromptTurn(line);
                }
            }
            return 0;
        } catch (IOException e) {
            log.error("REPL initialization failed: {}", e.getMessage(), e);
            return 1;
        }
    }

    private SlashCommandResult dispatchSlash(String line) {
        SlashContext ctx = new SlashContext(
            env.conversation,
            env.config,
            env.session,
            () -> runningUsage,
            env.conversation::clearHistory,
            out);
        SlashCommandResult result = slashRegistry.dispatch(line, ctx);
        if (result instanceof SlashCommandResult.Unknown u) {
            out.println("Unknown command: /" + u.commandName() + "  (try /help)");
        }
        return result;
    }

    private void runPromptTurn(String prompt) {
        TextRenderer renderer = new TextRenderer(out, style);
        int historyBefore = env.conversation.history().size();
        try {
            AssistantEventHandler handler = new AssistantEventHandler() {
                @Override
                public void onTextDelta(String text) {
                    renderer.onEvent(new AssistantEvent.TextDelta(text));
                }

                @Override
                public void onToolUseEnd(String id, String name, JsonNode input) {
                    renderer.onEvent(new AssistantEvent.ToolUseRequested(
                        id, name, input == null ? "{}" : input.toString()));
                }

                @Override
                public void onToolResult(String id, String name, String output, boolean isError) {
                    renderer.onEvent(new AssistantEvent.ToolResult(id, name, output, isError));
                }

                @Override
                public void onUsage(Usage usage) {
                    runningUsage = runningUsage.plus(usage);
                    renderer.onEvent(new AssistantEvent.UsageReport(runningUsage));
                }

                @Override
                public void onTurnFinish(String stopReason, int turns) {
                    renderer.onEvent(new AssistantEvent.TurnFinish(stopReason));
                }
            };
            env.conversation.runTurn(prompt, handler);
            env.persistNewHistory(historyBefore);
        } catch (RuntimeException e) {
            out.println();
            out.println("Turn failed: " + e.getMessage());
        }
    }

    private static Path historyPath() {
        return Path.of(System.getProperty("user.home"), ".local", "share", "jcc", "repl_history");
    }
}
