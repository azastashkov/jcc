package io.clawcode.commands.builtin;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.clawcode.commands.SlashCommand;
import io.clawcode.commands.SlashCommandResult;
import io.clawcode.commands.SlashContext;
import io.clawcode.core.JsonMapper;

public final class ConfigCommand implements SlashCommand {

    @Override
    public String name() {
        return "config";
    }

    @Override
    public String help() {
        return "Show the merged runtime config as JSON.";
    }

    @Override
    public SlashCommandResult handle(SlashContext ctx, String[] args) {
        try {
            String pretty = JsonMapper.shared()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(ctx.config());
            ctx.out().println(pretty);
        } catch (JsonProcessingException e) {
            ctx.out().println("Failed to render config: " + e.getMessage());
        }
        return SlashCommandResult.Continue.INSTANCE;
    }
}
