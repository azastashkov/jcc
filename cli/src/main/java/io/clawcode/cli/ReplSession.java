package io.clawcode.cli;

import io.clawcode.commands.SlashCommandRegistry;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;
import io.clawcode.core.ContentBlock;
import io.clawcode.core.Usage;
import io.clawcode.runtime.AssistantEventHandler;
import io.clawcode.runtime.ConversationRuntime;
import io.clawcode.runtime.Session;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

public final class ReplSession {

    private final RuntimeEnvironment env;
    private final SlashCommandRegistry slashRegistry;
    private final PrintStream out;
    private Usage runningUsage = Usage.EMPTY;

    public ReplSession(RuntimeEnvironment env, SlashCommandRegistry registry, PrintStream out) {
        this.env = env;
        this.slashRegistry = registry;
        this.out = out;
    }

    public int run() {
        try (Terminal terminal = TerminalBuilder.builder()
            .system(true)
            .dumb(true)
            .build()) {

            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("claw")
                .variable(LineReader.HISTORY_FILE, historyPath().toString())
                .completer(new StringsCompleter(slashRegistry.names().stream()
                    .map(n -> "/" + n)
                    .toList()))
                .build();

            out.println("Claw REPL — type /help for commands, /exit to quit.");
            out.println("Session: " + env.session.sessionId() + "  model=" + env.model);
            out.println();

            while (true) {
                String line;
                try {
                    line = reader.readLine("claw> ");
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
            System.err.println("REPL initialization failed: " + e.getMessage());
            return 1;
        }
    }

    private SlashCommandResult dispatchSlash(String line) {
        SlashContext ctx = new SlashContext(
            env.conversation,
            env.config,
            env.session,
            () -> runningUsage,
            this::clearConversationHistory,
            out);
        SlashCommandResult result = slashRegistry.dispatch(line, ctx);
        if (result instanceof SlashCommandResult.Unknown u) {
            out.println("Unknown command: /" + u.commandName() + "  (try /help)");
        }
        return result;
    }

    private void runPromptTurn(String prompt) {
        TextRenderer renderer = new TextRenderer(out);
        int historyBefore = env.conversation.history().size();
        try {
            AssistantEventHandler handler = new AssistantEventHandler() {
                @Override
                public void onTextDelta(String text) {
                    renderer.onEvent(new AssistantEvent.TextDelta(text));
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

    private void clearConversationHistory() {
        ConversationRuntime fresh = new ConversationRuntime(
            env.provider, env.tools, env.permissions,
            io.clawcode.runtime.PermissionPrompter.DENY_ALL,
            env.toolCtx, env.model, env.maxTokens, null);
        // Mutate env.conversation so references to it still see the cleared state.
        // Simplest: copy over by rebuilding conversation field not feasible since it's final.
        // Instead: clear the underlying list by running a no-op turn path — but there is no
        // public clear. For M4 we recreate the history by draining via a re-load; since Session
        // still persists the full history, the easiest semantic is: drop in-memory history and
        // refresh from disk after a reset of the mutable list.
        // Pragmatic approach: we use the fact that ConversationRuntime.history() is a copy.
        // To actually drop history, we need a mutator — add one.
        fresh.toString(); // placeholder to avoid unused-var; the real clear is below.
        env.conversation.clearHistory();
    }

    private static Path historyPath() {
        return Path.of(System.getProperty("user.home"), ".local", "share", "clawcode", "repl_history");
    }
}
