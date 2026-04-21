package io.jcc.commands.builtin;

import io.jcc.commands.SlashCommand;
import io.jcc.commands.SlashCommandResult;
import io.jcc.commands.SlashContext;
import io.jcc.runtime.Session;

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
