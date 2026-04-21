package io.clawcode.commands.builtin;

import io.clawcode.commands.SlashCommand;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;

public final class ExitCommand implements SlashCommand {

    @Override
    public String name() {
        return "exit";
    }

    @Override
    public String help() {
        return "Exit the REPL.";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        return SlashCommandResult.Exit.INSTANCE;
    }
}
