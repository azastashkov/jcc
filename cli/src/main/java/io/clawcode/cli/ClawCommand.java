package io.clawcode.cli;

import io.clawcode.commands.SlashCommandRegistry;
import io.clawcode.runtime.SessionStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "claw",
    mixinStandardHelpOptions = true,
    version = "claw 0.1.0-SNAPSHOT",
    description = "Interactive CLI agent for Claude and OpenAI-compatible LLMs.",
    subcommands = { PromptSubcommand.class }
)
public final class ClawCommand implements Callable<Integer> {

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
