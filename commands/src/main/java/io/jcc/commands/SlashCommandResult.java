package io.jcc.commands;

public sealed interface SlashCommandResult {

    record Continue() implements SlashCommandResult {
        public static final Continue INSTANCE = new Continue();
    }

    record Exit() implements SlashCommandResult {
        public static final Exit INSTANCE = new Exit();
    }

    record Rendered(String message) implements SlashCommandResult {}

    record Unknown(String commandName) implements SlashCommandResult {}
}
