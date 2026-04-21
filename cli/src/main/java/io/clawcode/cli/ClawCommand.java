package io.clawcode.cli;

import picocli.CommandLine.Command;

@Command(
    name = "claw",
    mixinStandardHelpOptions = true,
    version = "claw 0.1.0-SNAPSHOT",
    description = "Interactive CLI agent for Claude and OpenAI-compatible LLMs.",
    subcommands = { PromptSubcommand.class }
)
public final class ClawCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("claw: no subcommand specified. Run 'claw --help' for usage.");
    }
}
