package io.jcc.commands.builtin;

import io.jcc.commands.SlashCommand;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;

public final class McpStubCommand implements SlashCommand {

    @Override
    public String name() {
        return "mcp";
    }

    @Override
    public String help() {
        return "List configured MCP servers and their tools.";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        ctx.out().println("(MCP support arrives in M5 — no servers configured yet.)");
        return SlashCommandResult.Continue.INSTANCE;
    }
}
