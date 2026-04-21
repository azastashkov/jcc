package io.jcc.commands;

import io.jcc.core.Usage;
import io.jcc.runtime.ConversationRuntime;
import io.jcc.runtime.RuntimeConfig;
import io.jcc.runtime.Session;

import java.io.PrintStream;
import java.util.function.Supplier;

public record SlashContext(
    ConversationRuntime conversation,
    RuntimeConfig config,
    Session session,
    Supplier<Usage> totalUsage,
    Runnable clearHistory,
    PrintStream out
) {}
