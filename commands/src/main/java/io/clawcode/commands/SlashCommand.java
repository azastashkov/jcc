package io.clawcode.commands;

public interface SlashCommand {

    String name();

    String help();

    SlashCommandResult handle(SlashContext ctx, String[] args);
}
