package io.clawcode.commands;

import io.clawcode.core.Usage;
import io.clawcode.runtime.ConversationRuntime;
import io.clawcode.runtime.RuntimeConfig;
import io.clawcode.runtime.Session;

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
