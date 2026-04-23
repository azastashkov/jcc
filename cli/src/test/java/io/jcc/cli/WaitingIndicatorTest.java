package io.jcc.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingIndicatorTest {

    private ByteArrayOutputStream buf;
    private PrintStream out;

    @BeforeEach
    void setup() {
        buf = new ByteArrayOutputStream();
        out = new PrintStream(buf, true, StandardCharsets.UTF_8);
    }

    @AfterEach
    void teardown() {
        out.close();
    }

    // --- verb mapping ---

    @Test
    void toolPhaseMapsKnownTools() {
        assertThat(WaitingIndicator.toolPhase("read_file")).isEqualTo("Reading");
        assertThat(WaitingIndicator.toolPhase("write_file")).isEqualTo("Writing");
        assertThat(WaitingIndicator.toolPhase("edit_file")).isEqualTo("Editing");
        assertThat(WaitingIndicator.toolPhase("glob")).isEqualTo("Searching");
        assertThat(WaitingIndicator.toolPhase("grep")).isEqualTo("Searching");
        assertThat(WaitingIndicator.toolPhase("bash")).isEqualTo("Running shell");
        assertThat(WaitingIndicator.toolPhase("web_fetch")).isEqualTo("Fetching web");
        assertThat(WaitingIndicator.toolPhase("web_search")).isEqualTo("Searching web");
        assertThat(WaitingIndicator.toolPhase("Agent")).isEqualTo("Thinking in subagent");
    }

    @Test
    void toolPhaseFallsBackForUnknownTool() {
        assertThat(WaitingIndicator.toolPhase("custom_mcp_tool"))
            .isEqualTo("Running custom_mcp_tool");
    }

    @Test
    void toolPhaseHandlesNullAndEmpty() {
        assertThat(WaitingIndicator.toolPhase(null)).isEqualTo("Running");
        assertThat(WaitingIndicator.toolPhase("")).isEqualTo("Running");
    }

    // --- PLAIN style: no animation, run() still delegates ---

    @Test
    void plainStyleSkipsAnimationEntirely() {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.PLAIN)) {
            ind.begin("Waiting");
            ind.updateTokens(100, 50);
            ind.tick();
            ind.tick();
        }
        // No ESC bytes, no \r — purely silent
        for (byte b : buf.toByteArray()) {
            assertThat(b).isNotEqualTo((byte) 0x1b);
            assertThat(b).isNotEqualTo((byte) '\r');
        }
    }

    @Test
    void runDelegatesToWriterEvenInPlainMode() {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.PLAIN)) {
            ind.run(() -> out.println("hello"));
        }
        assertThat(buf.toString(StandardCharsets.UTF_8)).contains("hello");
    }

    // --- colored style: animation, erase, repaint ---

    @Test
    void beginPrintsPhaseLineOnTick() {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.colored())) {
            ind.begin("Waiting for response");
            ind.tick();
            String result = buf.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("Waiting for response");
            assertThat(result).containsAnyOf("✻", "✺", "✹", "✸", "✶", "✳", "✲");
        }
    }

    @Test
    void tickIncludesElapsedSeconds() {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.colored())) {
            ind.begin("Working");
            ind.tick();
            assertThat(buf.toString(StandardCharsets.UTF_8)).contains("(0s");
        }
    }

    @Test
    void tickIncludesTokenCountsWhenNonZero() {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.colored())) {
            ind.begin("Working");
            ind.updateTokens(1234, 567);
            ind.tick();
            String result = buf.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("sent=1234").contains("recv=567");
        }
    }

    @Test
    void runErasesActiveSpinnerBeforeWriting() {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.colored())) {
            ind.begin("Waiting");
            ind.tick();
            ind.run(() -> out.print("HELLO"));
        }
        String result = buf.toString(StandardCharsets.UTF_8);
        int helloIdx = result.indexOf("HELLO");
        assertThat(helloIdx).isGreaterThanOrEqualTo(0);
        // The erase sequence must precede HELLO (since tick painted before run)
        int eraseIdx = result.lastIndexOf("\r\033[K", helloIdx);
        assertThat(eraseIdx).as("erase must precede HELLO").isGreaterThanOrEqualTo(0);
    }

    @Test
    void endStopsTicksFromPainting() {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.colored())) {
            ind.begin("Waiting");
            ind.tick();
            int beforeEnd = buf.size();
            ind.end();
            int afterEnd = buf.size();
            // end() may emit erase if visible, OK
            ind.tick();
            // tick after end is a no-op
            assertThat(buf.size()).isEqualTo(afterEnd);
            assertThat(afterEnd).isGreaterThanOrEqualTo(beforeEnd);
        }
    }

    @Test
    void closeIsIdempotent() {
        WaitingIndicator ind = new WaitingIndicator(out, Style.colored());
        ind.begin("X");
        ind.close();
        ind.close();  // must not throw
    }

    @Test
    void afterCloseTickIsNoOp() {
        WaitingIndicator ind = new WaitingIndicator(out, Style.colored());
        ind.begin("X");
        ind.tick();
        ind.close();
        int afterClose = buf.size();
        ind.tick();
        assertThat(buf.size()).isEqualTo(afterClose);
    }

    // --- concurrency: ticks and run() never interleave each other's bytes ---

    @Test
    void concurrentTicksAndRunsDoNotInterleaveBytes() throws Exception {
        try (WaitingIndicator ind = new WaitingIndicator(out, Style.colored())) {
            ind.begin("Working");
            ExecutorService exec = Executors.newFixedThreadPool(8);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> tasks = new ArrayList<>();
                int n = 200;
                for (int i = 0; i < n; i++) {
                    final int idx = i;
                    tasks.add(exec.submit(() -> {
                        start.await();
                        ind.run(() -> out.print("[TAG" + idx + "]"));
                        return null;
                    }));
                }
                Future<?> ticker = exec.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        ind.tick();
                        Thread.sleep(1);
                    }
                    return null;
                });
                start.countDown();
                for (Future<?> f : tasks) f.get(5, TimeUnit.SECONDS);
                ticker.get(5, TimeUnit.SECONDS);
            } finally {
                exec.shutdownNow();
                exec.awaitTermination(2, TimeUnit.SECONDS);
            }
        }
        String result = buf.toString(StandardCharsets.UTF_8);
        for (int i = 0; i < 200; i++) {
            assertThat(result).as("tag [TAG%d] must appear intact", i).contains("[TAG" + i + "]");
        }
    }
}
