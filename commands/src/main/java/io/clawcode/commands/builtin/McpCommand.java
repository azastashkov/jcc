package io.clawcode.commands.builtin;

import io.clawcode.commands.SlashCommand;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;
import io.clawcode.runtime.mcp.McpServerManager;
import io.clawcode.runtime.mcp.McpTool;

import java.util.function.Supplier;

public final class McpCommand implements SlashCommand {

    private final Supplier<McpServerManager> manager;

    public McpCommand(Supplier<McpServerManager> manager) {
        this.manager = manager;
    }

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
        McpServerManager mgr = manager.get();
        if (mgr == null || mgr.serverNames().isEmpty()) {
            ctx.out().println("No MCP servers configured. Add entries under 'mcp' in .claw.json.");
            return SlashCommandResult.Continue.INSTANCE;
        }
        ctx.out().println("MCP servers:");
        for (String server : mgr.serverNames()) {
            ctx.out().println("  " + server);
        }
        ctx.out().println();
        ctx.out().println("Tools:");
        for (McpTool tool : mgr.toolList()) {
            ctx.out().printf("  %s — %s%n",
                tool.qualifiedName(),
                tool.description() == null ? "(no description)" : tool.description());
        }
        return SlashCommandResult.Continue.INSTANCE;
    }
}
