package io.clawcode.cli;

import io.clawcode.core.Usage;

import java.io.PrintStream;

public final class TextRenderer implements StreamingRenderer {

    private final PrintStream out;
    private boolean sawText;
    private Usage lastUsage = Usage.EMPTY;

    public TextRenderer(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(AssistantEvent event) {
        switch (event) {
            case AssistantEvent.TextDelta delta -> {
                out.print(delta.text());
                out.flush();
                sawText = true;
            }
            case AssistantEvent.Thinking ignored -> {
            }
            case AssistantEvent.ToolUseRequested ignored -> {
            }
            case AssistantEvent.UsageReport report -> lastUsage = report.usage();
            case AssistantEvent.TurnFinish ignored -> {
                if (sawText) {
                    out.println();
                }
                out.printf(
                    "%n[tokens in=%d out=%d cache_read=%d cache_write=%d]%n",
                    lastUsage.inputTokens(),
                    lastUsage.outputTokens(),
                    lastUsage.cacheReadInputTokens(),
                    lastUsage.cacheCreationInputTokens());
            }
        }
    }

    @Override
    public void close() {
        out.flush();
    }
}
