package io.jcc.commands.builtin;

import io.jcc.commands.SlashCommand;
import io.jcc.commands.SlashCommandRegistry;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;

public final class HelpCommand implements SlashCommand {

    private SlashCommandRegistry registry;

    public void bind(SlashCommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String help() {
        return "Show available slash commands.";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        StringBuilder sb = new StringBuilder("Available commands:\n");
        if (registry != null) {
            for (SlashCommand cmd : registry.commandList()) {
                sb.append(String.format("  /%-10s %s%n", cmd.name(), cmd.help()));
            }
        }
        ctx.out().print(sb);
        return SlashCommandResult.Continue.INSTANCE;
    }
}
