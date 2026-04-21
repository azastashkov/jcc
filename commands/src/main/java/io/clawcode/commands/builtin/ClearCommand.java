package io.clawcode.commands.builtin;

import io.clawcode.commands.SlashCommand;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;

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
