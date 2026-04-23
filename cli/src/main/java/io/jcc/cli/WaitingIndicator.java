package io.jcc.cli;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class WaitingIndicator implements AutoCloseable {

    private static final String[] FRAMES = { "✻", "✺", "✹", "✸", "✶", "✳", "✲" };
    private static final long TICK_PERIOD_MS = 100;
    private static final int MAX_PHASE_CHARS = 40;
    private static final String ERASE_LINE = "\r\033[K";

    private static final Map<String, String> TOOL_PHASES = Map.ofEntries(
        Map.entry("read_file", "Reading"),
        Map.entry("write_file", "Writing"),
        Map.entry("edit_file", "Editing"),
        Map.entry("glob", "Searching"),
        Map.entry("grep", "Searching"),
        Map.entry("bash", "Running shell"),
        Map.entry("web_fetch", "Fetching web"),
        Map.entry("web_search", "Searching web"),
        Map.entry("Agent", "Thinking in subagent")
    );

    public static String toolPhase(String toolName) {
        if (toolName == null || toolName.isEmpty()) return "Running";
        String mapped = TOOL_PHASES.get(toolName);
        return mapped != null ? mapped : "Running " + toolName;
    }

    private final PrintStream out;
    private final Style style;
    private final boolean animate;
    private final ScheduledExecutorService scheduler;
    private final Object lock = new Object();

    private String currentPhase;
    private long phaseStartedAtMs;
    private long sentTokens;
    private long recvTokens;
    private int frameIndex;
    private boolean visible;
    private ScheduledFuture<?> scheduled;
    private boolean closed;

    public WaitingIndicator(PrintStream out, Style style) {
        this.out = out;
        this.style = style;
        this.animate = style.isColor();
        this.scheduler = animate
            ? Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "jcc-waiting-indicator");
                t.setDaemon(true);
                return t;
            })
            : null;
    }

    public void begin(String phase) {
        synchronized (lock) {
            if (closed || !animate) return;
            String trimmed = trimPhase(phase);
            if (trimmed.equals(currentPhase)) return;
            currentPhase = trimmed;
            phaseStartedAtMs = System.currentTimeMillis();
            if (scheduled == null) {
                scheduled = scheduler.scheduleAtFixedRate(
                    this::tick, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void updateTokens(long sent, long recv) {
        synchronized (lock) {
            this.sentTokens = sent;
            this.recvTokens = recv;
        }
    }

    public void end() {
        synchronized (lock) {
            if (!animate) return;
            currentPhase = null;
            erase();
        }
    }

    public void run(Runnable writer) {
        synchronized (lock) {
            erase();
            writer.run();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            if (scheduled != null) {
                scheduled.cancel(false);
                scheduled = null;
            }
            currentPhase = null;
            erase();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void tick() {
        synchronized (lock) {
            if (closed || currentPhase == null || !animate) return;
            erase();
            String frame = FRAMES[frameIndex];
            frameIndex = (frameIndex + 1) % FRAMES.length;
            long elapsedSec = (System.currentTimeMillis() - phaseStartedAtMs) / 1000;
            String body = (sentTokens == 0 && recvTokens == 0)
                ? String.format("%s %s… (%ds)", frame, currentPhase, elapsedSec)
                : String.format("%s %s… (%ds · sent=%d recv=%d)",
                    frame, currentPhase, elapsedSec, sentTokens, recvTokens);
            out.print(style.progress(body));
            out.flush();
            visible = true;
        }
    }

    private void erase() {
        if (!visible) return;
        out.print(ERASE_LINE);
        out.flush();
        visible = false;
    }

    private static String trimPhase(String phase) {
        if (phase.length() <= MAX_PHASE_CHARS) return phase;
        return phase.substring(0, MAX_PHASE_CHARS) + "…";
    }
}
