package io.jcc.commands.builtin;

import io.jcc.commands.SlashCommand;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;

public final class CompactCommand implements SlashCommand {

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public String help() {
        return "Summarize and trim the conversation history (planned — currently a no-op stub).";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        ctx.out().println("/compact is not yet implemented. History is preserved verbatim.");
        ctx.out().println("Hint: use /clear if you want to drop the in-memory history.");
        return SlashCommandResult.Continue.INSTANCE;
    }
}
