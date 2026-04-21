package io.clawcode.commands;

import io.clawcode.commands.builtin.ClearCommand;
import io.clawcode.commands.builtin.CompactCommand;
import io.clawcode.commands.builtin.ConfigCommand;
import io.clawcode.commands.builtin.CostCommand;
import io.clawcode.commands.builtin.ExitCommand;
import io.clawcode.commands.builtin.HelpCommand;
import io.clawcode.commands.builtin.McpCommand;
import io.clawcode.commands.builtin.McpStubCommand;
import io.clawcode.commands.builtin.SkillsStubCommand;
import io.clawcode.commands.builtin.StatusCommand;
import io.clawcode.commands.builtin.SubagentCommand;
import io.clawcode.runtime.mcp.McpServerManager;
import io.clawcode.runtime.subagent.TaskRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SlashCommandRegistry {

    private final Map<String, SlashCommand> commands;

    public SlashCommandRegistry(List<SlashCommand> commands) {
        LinkedHashMap<String, SlashCommand> map = new LinkedHashMap<>();
        for (SlashCommand c : commands) {
            map.put(c.name(), c);
        }
        this.commands = Map.copyOf(map);
    }

    public static SlashCommandRegistry defaults() {
        return build(() -> null, () -> null);
    }

    public static SlashCommandRegistry withMcp(java.util.function.Supplier<McpServerManager> mcp) {
        return build(mcp, () -> null);
    }

    public static SlashCommandRegistry build(
        java.util.function.Supplier<McpServerManager> mcp,
        java.util.function.Supplier<TaskRegistry> tasks
    ) {
        HelpCommand help = new HelpCommand();
        SlashCommandRegistry registry = new SlashCommandRegistry(List.of(
            help,
            new StatusCommand(),
            new CostCommand(),
            new ClearCommand(),
            new CompactCommand(),
            new ConfigCommand(),
            mcp == null ? new McpStubCommand() : new McpCommand(mcp),
            new SkillsStubCommand(),
            new SubagentCommand(tasks == null ? () -> null : tasks),
            new ExitCommand()));
        help.bind(registry);
        return registry;
    }

    public List<SlashCommand> commandList() {
        return List.copyOf(commands.values());
    }

    public List<String> names() {
        return commandList().stream().map(SlashCommand::name).toList();
    }

    public SlashCommandResult dispatch(String line, SlashContext ctx) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("/")) {
            return new SlashCommandResult.Unknown(trimmed);
        }
        String body = trimmed.substring(1);
        String[] parts = body.isEmpty() ? new String[]{""} : body.split("\\s+");
        String name = parts[0];
        String[] args = parts.length > 1
            ? java.util.Arrays.copyOfRange(parts, 1, parts.length)
            : new String[0];

        SlashCommand cmd = commands.get(name);
        if (cmd == null) {
            return new SlashCommandResult.Unknown(name);
        }
        return cmd.handle(ctx, args);
    }
}
