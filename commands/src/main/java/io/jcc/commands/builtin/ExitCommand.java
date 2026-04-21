package io.jcc.commands.builtin;

import io.jcc.commands.SlashCommand;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;

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
