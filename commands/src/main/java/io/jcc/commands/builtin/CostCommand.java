package io.jcc.commands.builtin;

import io.jcc.commands.SlashCommand;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;
import io.jcc.core.Usage;

public final class CostCommand implements SlashCommand {

    @Override
    public String name() {
        return "cost";
    }

    @Override
    public String help() {
        return "Show token usage accumulated this session.";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        Usage u = ctx.totalUsage().get();
        ctx.out().printf(
            "tokens: input=%d output=%d cache_read=%d cache_write=%d total=%d%n",
            u.inputTokens(), u.outputTokens(),
            u.cacheReadInputTokens(), u.cacheCreationInputTokens(), u.totalTokens());
        return SlashCommandResult.Continue.INSTANCE;
    }
}
