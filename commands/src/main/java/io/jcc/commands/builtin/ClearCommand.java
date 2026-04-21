package io.jcc.commands.builtin;

import io.jcc.commands.SlashCommand;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;

public final class ClearCommand implements SlashCommand {

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public String help() {
        return "Clear the in-memory conversation history (does not delete the session file).";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        ctx.clearHistory().run();
        ctx.out().println("Cleared conversation history.");
        return SlashCommandResult.Continue.INSTANCE;
    }
}
