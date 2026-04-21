package io.clawcode.commands.builtin;

import io.clawcode.commands.SlashCommand;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;
import io.clawcode.runtime.Session;

public final class StatusCommand implements SlashCommand {

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String help() {
        return "Show session, model, and permission state.";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        Session s = ctx.session();
        StringBuilder sb = new StringBuilder();
        sb.append("model:        ").append(ctx.config().model() == null ? "(none)" : ctx.config().model()).append('\n');
        sb.append("session:      ").append(s == null ? "(none)" : s.sessionId()).append('\n');
        sb.append("session file: ").append(s == null ? "(none)" : s.jsonlPath()).append('\n');
        sb.append("messages:     ").append(s == null ? 0 : s.messages().size()).append('\n');
        sb.append("permissions:  ")
            .append(ctx.config().permissions().defaultMode() != null
                ? ctx.config().permissions().defaultMode().cliName()
                : "(default)")
            .append('\n');
        ctx.out().print(sb);
        return SlashCommandResult.Continue.INSTANCE;
    }
}
