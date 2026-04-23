package io.jcc.cli;

import io.jcc.cli.highlight.HighlighterRegistry;
import io.jcc.core.Usage;

import java.io.PrintStream;

public final class TextRenderer implements StreamingRenderer {

    private final PrintStream out;
    private final Style style;
    private final MarkdownTextFilter filter;
    private boolean sawText;
    private Usage lastUsage = Usage.EMPTY;
    private long lastPrintedSent = -1;
    private long lastPrintedRecv = -1;
    private long prevSent;
    private long lastSubTurnInput;
    private int contextWindow;

    public TextRenderer(PrintStream out) {
        this(out, Style.detect(), HighlighterRegistry.defaults());
    }

    public TextRenderer(PrintStream out, Style style) {
        this(out, style, HighlighterRegistry.defaults());
    }

    public TextRenderer(PrintStream out, Style style, HighlighterRegistry highlighters) {
        this.out = out;
        this.style = style;
        this.filter = new MarkdownTextFilter(out, style, highlighters);
    }

    public TextRenderer setContextWindow(int contextWindow) {
        this.contextWindow = contextWindow;
        return this;
    }

    @Override
    public void onEvent(AssistantEvent event) {
        switch (event) {
            case AssistantEvent.TextDelta delta -> {
                filter.appendText(delta.text());
                sawText = true;
            }
            case AssistantEvent.Thinking ignored -> {
            }
            case AssistantEvent.ToolUseRequested use -> {
                filter.flush();
                breakInlineText();
                out.printf("%s %s %s%n",
                    style.toolMarker("●"),
                    style.toolName(use.name()),
                    style.toolArgs(abbreviate(use.inputJson(), 200)));
                out.flush();
            }
            case AssistantEvent.ToolResult result -> {
                filter.flush();
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
                long delta = sent - prevSent;
                prevSent = sent;
                if (delta > 0) lastSubTurnInput = delta;
                if (sent == 0 && recv == 0) break;
                if (sent == lastPrintedSent && recv == lastPrintedRecv) break;
                lastPrintedSent = sent;
                lastPrintedRecv = recv;
                filter.flush();
                breakInlineText();
                StringBuilder line = new StringBuilder();
                line.append(String.format("  · sent=%d recv=%d", sent, recv));
                int pct = contextPct();
                if (pct >= 0) line.append(" ctx=").append(pct).append('%');
                out.printf("%s%n", style.progress(line.toString()));
                out.flush();
            }
            case AssistantEvent.TurnFinish finish -> {
                filter.flush();
                if (sawText) {
                    out.println();
                }
                String reason = finish.stopReason();
                if (reason != null && !reason.isBlank() && !"end_turn".equals(reason)) {
                    out.printf("%n%s%n", style.stopReason("[stop: " + reason + "]"));
                }
                StringBuilder footer = new StringBuilder();
                footer.append(String.format(
                    "[tokens in=%d out=%d cache_read=%d cache_write=%d",
                    lastUsage.inputTokens(),
                    lastUsage.outputTokens(),
                    lastUsage.cacheReadInputTokens(),
                    lastUsage.cacheCreationInputTokens()));
                int pct = contextPct();
                if (pct >= 0) {
                    footer.append(String.format(" · ctx=%d%% (%s/%s)",
                        pct, abbreviateTokens(lastSubTurnInput), abbreviateTokens(contextWindow)));
                }
                footer.append(']');
                out.printf("%n%s%n", style.tokenFooter(footer.toString()));
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

    private int contextPct() {
        if (contextWindow <= 0 || lastSubTurnInput <= 0) return -1;
        return (int) Math.round(100.0 * lastSubTurnInput / contextWindow);
    }

    static String abbreviateTokens(long n) {
        if (n >= 1_000_000) {
            return n % 1_000_000 == 0
                ? (n / 1_000_000) + "M"
                : String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 1_000) {
            return n % 1_000 == 0
                ? (n / 1_000) + "K"
                : String.format(java.util.Locale.ROOT, "%.1fK", n / 1_000.0);
        }
        return String.valueOf(n);
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    @Override
    public void close() {
        filter.flush();
        out.flush();
    }
}
