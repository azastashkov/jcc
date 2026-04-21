package io.jcc.cli;

import io.jcc.commands.SlashCommandRegistry;
import io.jcc.runtime.SessionStore;
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

    @Option(names = "--model", description = "Model name or alias (opus, sonnet, haiku).")
    String model;

    @Option(names = "--max-tokens", description = "Maximum tokens in the response.")
    Integer maxTokens;

    @Option(names = "--permission-mode", description = "Permission mode.")
    String permissionMode;

    @Option(names = "--resume", description = "Resume a prior session by id, filename, 'latest', or path.")
    String resume;

    @Override
    public Integer call() {
        RuntimeEnvironment env;
        try {
            env = RuntimeEnvironment.bootstrap(
                new RuntimeEnvironment.Options(model, maxTokens, permissionMode, resume),
                SessionStore.defaultStore());
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 2;
        }
        return new ReplSession(env, SlashCommandRegistry.defaults(), System.out).run();
    }
}
