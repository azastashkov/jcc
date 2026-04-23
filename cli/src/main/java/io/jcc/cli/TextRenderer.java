package io.jcc.cli;

import io.jcc.core.Usage;

import java.io.PrintStream;

public final class TextRenderer implements StreamingRenderer {

    private final PrintStream out;
    private final Style style;
    private boolean sawText;
    private Usage lastUsage = Usage.EMPTY;
    private long lastPrintedSent = -1;
    private long lastPrintedRecv = -1;

    public TextRenderer(PrintStream out) {
        this(out, Style.detect());
    }

    public TextRenderer(PrintStream out, Style style) {
        this.out = out;
        this.style = style;
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
                out.printf("%s %s %s%n",
                    style.toolMarker("●"),
                    style.toolName(use.name()),
                    style.toolArgs(abbreviate(use.inputJson(), 200)));
                out.flush();
            }
            case AssistantEvent.ToolResult result -> {
                breakInlineText();
                String body = abbreviate(firstLine(result.output()), 300);
                if (result.isError()) {
                    out.printf("%s %s%n",
                        style.toolError("  error →"),
                        style.toolError(body));
                } else {
                    out.printf("%s %s%n",
                        style.toolResult("  →"),
                        style.toolResult(body));
                }
                out.flush();
            }
            case AssistantEvent.UsageReport report -> {
                lastUsage = report.usage();
                long sent = sentTokens(lastUsage);
                long recv = lastUsage.outputTokens();
                if (sent == 0 && recv == 0) break;
                if (sent == lastPrintedSent && recv == lastPrintedRecv) break;
                lastPrintedSent = sent;
                lastPrintedRecv = recv;
                breakInlineText();
                out.printf("%s%n", style.progress(String.format("  · sent=%d recv=%d", sent, recv)));
                out.flush();
            }
            case AssistantEvent.TurnFinish finish -> {
                if (sawText) {
                    out.println();
                }
                String reason = finish.stopReason();
                if (reason != null && !reason.isBlank() && !"end_turn".equals(reason)) {
                    out.printf("%n%s%n", style.stopReason("[stop: " + reason + "]"));
                }
                out.printf("%n%s%n", style.tokenFooter(String.format(
                    "[tokens in=%d out=%d cache_read=%d cache_write=%d]",
                    lastUsage.inputTokens(),
                    lastUsage.outputTokens(),
                    lastUsage.cacheReadInputTokens(),
                    lastUsage.cacheCreationInputTokens())));
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

    private static long sentTokens(Usage u) {
        return (long) u.inputTokens()
            + u.cacheCreationInputTokens()
            + u.cacheReadInputTokens();
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
