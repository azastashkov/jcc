package io.jcc.cli;

import io.jcc.commands.SlashCommandRegistry;
import io.jcc.runtime.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "jcc",
    mixinStandardHelpOptions = true,
    version = "jcc 0.1.0-SNAPSHOT",
    description = "Interactive CLI agent for Claude and OpenAI-compatible LLMs.",
    subcommands = { PromptSubcommand.class }
)
public final class JccCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(JccCommand.class);

    @Option(names = "--model", description = "Model name or alias (opus, sonnet, haiku).")
    String model;

    @Option(names = "--max-tokens", description = "Maximum tokens in the response.")
    Integer maxTokens;

    @Option(names = "--permission-mode", description = "Permission mode.")
    String permissionMode;

    @Option(names = "--resume", description = "Resume a prior session by id, filename, 'latest', or path.")
    String resume;

    @Option(names = "--no-color", description = "Disable ANSI colors and the in-place status line.")
    boolean noColor;

    @Override
    public Integer call() {
        RuntimeEnvironment env;
        try {
            env = RuntimeEnvironment.bootstrap(
                new RuntimeEnvironment.Options(model, maxTokens, permissionMode, resume),
                SessionStore.defaultStore());
        } catch (IllegalStateException e) {
            log.error("{}", e.getMessage());
            return 2;
        }
        Style style = noColor ? Style.PLAIN : Style.detect();
        return new ReplSession(env, SlashCommandRegistry.defaults(), System.out, style).run();
    }
}
