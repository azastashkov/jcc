package io.clawcode.commands.builtin;

import io.clawcode.commands.SlashCommand;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;

public final class SkillsStubCommand implements SlashCommand {

    @Override
    public String name() {
        return "skills";
    }

    @Override
    public String help() {
        return "List installed skills.";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        ctx.out().println("(Skills discovery is not part of the MVP. No skills installed.)");
        return SlashCommandResult.Continue.INSTANCE;
    }
}
