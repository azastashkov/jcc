package io.clawcode.runtime;

import io.clawcode.core.ContentBlock;
import io.clawcode.core.MessageRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    void appendAndReloadPreservesMessages(@TempDir Path dir) {
        Path file = dir.resolve("s.jsonl");
        Session s = Session.create(file, dir.toString(), "claude-opus-4-7");
        s.append(new ConversationMessage(MessageRole.USER,
            List.of(new ContentBlock.Text("hello")), null));
        s.append(new ConversationMessage(MessageRole.ASSISTANT,
            List.of(new ContentBlock.Text("hi!")), null));

        Session reloaded = Session.load(file);
        assertThat(reloaded.sessionId()).isEqualTo(s.sessionId());
        assertThat(reloaded.messages()).hasSize(2);
        assertThat(reloaded.messages().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(((ContentBlock.Text) reloaded.messages().get(0).blocks().get(0)).text())
            .isEqualTo("hello");
    }

    @Test
    void rotatesFileOnceThresholdExceeded(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("big.jsonl");
        Session s = Session.create(file, dir.toString(), "m");
        String padding = "x".repeat(2048);
        for (int i = 0; i < 150; i++) {
            s.append(new ConversationMessage(MessageRole.USER,
                List.of(new ContentBlock.Text("turn " + i + " " + padding)), null));
        }
        assertThat(Files.exists(dir.resolve("big.jsonl"))).isTrue();
        assertThat(Files.exists(dir.resolve("big.jsonl.1"))).isTrue();
        assertThat(Files.size(file)).isLessThan(Session.ROTATE_AFTER_BYTES + 50_000);
    }

    @Test
    void rotationCapsAtMaxFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("r.jsonl");
        Session s = Session.create(file, null, null);
        String padding = "x".repeat(2048);
        for (int i = 0; i < 600; i++) {
            s.append(new ConversationMessage(MessageRole.USER,
                List.of(new ContentBlock.Text("turn " + i + " " + padding)), null));
        }
        assertThat(Files.exists(dir.resolve("r.jsonl"))).isTrue();
        assertThat(Files.exists(dir.resolve("r.jsonl.1"))).isTrue();
        assertThat(Files.exists(dir.resolve("r.jsonl.2"))).isTrue();
        assertThat(Files.exists(dir.resolve("r.jsonl.3"))).isTrue();
        assertThat(Files.exists(dir.resolve("r.jsonl.4"))).isFalse();
    }

    @Test
    void sessionStoreDiscoversLatest(@TempDir Path dir) throws Exception {
        SessionStore store = new SessionStore(dir);
        Session a = store.createNew(null, null);
        Thread.sleep(10);
        Session b = store.createNew(null, null);
        b.append(new ConversationMessage(MessageRole.USER,
            List.of(new ContentBlock.Text("latest")), null));

        Session latest = store.loadLatest();
        assertThat(latest.sessionId()).isEqualTo(b.sessionId());
    }

    @Test
    void sessionStoreResolvesByIdPrefix(@TempDir Path dir) {
        SessionStore store = new SessionStore(dir);
        Session a = store.createNew(null, null);
        Session loaded = store.load(a.jsonlPath().getFileName().toString());
        assertThat(loaded.sessionId()).isEqualTo(a.sessionId());
    }
}
