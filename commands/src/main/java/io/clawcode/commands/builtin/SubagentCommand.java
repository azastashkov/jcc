package io.clawcode.commands.builtin;

import io.clawcode.commands.SlashCommand;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;
import io.clawcode.runtime.subagent.TaskRecord;
import io.clawcode.runtime.subagent.TaskRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

public final class SubagentCommand implements SlashCommand {

    private final Supplier<TaskRegistry> registry;

    public SubagentCommand(Supplier<TaskRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "subagent";
    }

    @Override
    public String help() {
        return "Manage sub-agent tasks. Usage: /subagent list | /subagent stop <task-id>";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        TaskRegistry reg = registry.get();
        if (reg == null) {
            ctx.out().println("Sub-agent support is not wired in this environment.");
            return SlashCommandResult.Continue.INSTANCE;
        }

        if (args.length == 0 || "list".equals(args[0])) {
            Collection<TaskRecord> all = reg.all();
            if (all.isEmpty()) {
                ctx.out().println("No sub-agent tasks recorded yet.");
                return SlashCommandResult.Continue.INSTANCE;
            }
            ctx.out().println("task-id                          type             status     elapsed   description");
            for (TaskRecord t : all) {
                long elapsedMs = Instant.now().toEpochMilli() - t.createdAtMs();
                ctx.out().printf("%-32s %-16s %-10s %-9s %s%n",
                    t.taskId(),
                    t.subagentType() == null || t.subagentType().isEmpty() ? "(default)" : t.subagentType(),
                    t.status().wire(),
                    Duration.ofMillis(elapsedMs).toSeconds() + "s",
                    t.description());
            }
            return SlashCommandResult.Continue.INSTANCE;
        }

        if ("stop".equals(args[0])) {
            if (args.length < 2) {
                ctx.out().println("Usage: /subagent stop <task-id>");
                return SlashCommandResult.Continue.INSTANCE;
            }
            boolean stopped = reg.stop(args[1]);
            ctx.out().println(stopped ? "Stopped task " + args[1] : "Task not found: " + args[1]);
            return SlashCommandResult.Continue.INSTANCE;
        }

        ctx.out().println("Unknown /subagent subcommand: " + args[0]);
        return SlashCommandResult.Continue.INSTANCE;
    }
}
