package io.jcc.cli;

import io.jcc.core.Usage;

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
            case AssistantEvent.ToolUseRequested use -> {
                breakInlineText();
                out.printf("● %s %s%n", use.name(), abbreviate(use.inputJson(), 200));
                out.flush();
            }
            case AssistantEvent.ToolResult result -> {
                breakInlineText();
                String prefix = result.isError() ? "  error →" : "  →";
                out.printf("%s %s%n", prefix, abbreviate(firstLine(result.output()), 300));
                out.flush();
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

    private void breakInlineText() {
        if (sawText) {
            out.println();
            sawText = false;
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    @Override
    public void close() {
        out.flush();
    }
}
