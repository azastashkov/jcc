package io.jcc.commands;

public interface SlashCommand {

    String name();

    String help();

    SlashCommandResult handle(SlashContext ctx, String[] args);
}
