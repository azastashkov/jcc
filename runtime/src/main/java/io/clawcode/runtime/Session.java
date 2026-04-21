package io.clawcode.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clawcode.core.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class Session {

    public static final int VERSION = 1;
    public static final long ROTATE_AFTER_BYTES = 256L * 1024L;
    public static final int MAX_ROTATED_FILES = 3;

    private final ObjectMapper mapper = JsonMapper.shared();
    private final ReentrantLock writeLock = new ReentrantLock();

    private final Path jsonlPath;
    private final String sessionId;
    private final long createdAtMs;
    private long updatedAtMs;
    private String workspaceRoot;
    private String model;
    private final List<ConversationMessage> messages = new ArrayList<>();

    private Session(Path jsonlPath, String sessionId, long createdAtMs, long updatedAtMs,
                    String workspaceRoot, String model) {
        this.jsonlPath = jsonlPath;
        this.sessionId = sessionId;
        this.createdAtMs = createdAtMs;
        this.updatedAtMs = updatedAtMs;
        this.workspaceRoot = workspaceRoot;
        this.model = model;
    }

    public static Session create(Path jsonlPath, String workspaceRoot, String model) {
        long now = Instant.now().toEpochMilli();
        String id = "session-" + now + "-" + Integer.toHexString((int) (Math.random() * 0x10000));
        Session s = new Session(jsonlPath, id, now, now, workspaceRoot, model);
        try {
            Path parent = jsonlPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            s.writeMeta();
        } catch (IOException e) {
            throw new SessionException("Failed to create session " + jsonlPath, e);
        }
        return s;
    }

    public static Session load(Path jsonlPath) {
        Objects.requireNonNull(jsonlPath, "jsonlPath");
        if (!Files.exists(jsonlPath)) {
            throw new SessionException("Session file not found: " + jsonlPath);
        }
        ObjectMapper mapper = JsonMapper.shared();
        String sessionId = null;
        long createdAtMs = Instant.now().toEpochMilli();
        long updatedAtMs = createdAtMs;
        String workspaceRoot = null;
        String model = null;
        List<ConversationMessage> messages = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(jsonlPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                SessionEvent event = mapper.readValue(line, SessionEvent.class);
                switch (event) {
                    case SessionEvent.Meta meta -> {
                        sessionId = meta.sessionId();
                        createdAtMs = meta.createdAtMs();
                        updatedAtMs = meta.updatedAtMs();
                        workspaceRoot = meta.workspaceRoot();
                        model = meta.model();
                    }
                    case SessionEvent.Message m -> messages.add(m.message());
                }
            }
        } catch (IOException e) {
            throw new SessionException("Failed to load session " + jsonlPath, e);
        }

        if (sessionId == null) {
            throw new SessionException("Session file missing session_meta header: " + jsonlPath);
        }

        Session s = new Session(jsonlPath, sessionId, createdAtMs, updatedAtMs, workspaceRoot, model);
        s.messages.addAll(messages);
        return s;
    }

    public String sessionId() { return sessionId; }
    public long createdAtMs() { return createdAtMs; }
    public long updatedAtMs() { return updatedAtMs; }
    public Path jsonlPath() { return jsonlPath; }
    public String workspaceRoot() { return workspaceRoot; }
    public String model() { return model; }
    public List<ConversationMessage> messages() { return List.copyOf(messages); }

    public void append(ConversationMessage message) {
        writeLock.lock();
        try {
            rotateIfNeeded();
            writeEvent(new SessionEvent.Message(message));
            messages.add(message);
            updatedAtMs = Instant.now().toEpochMilli();
        } catch (IOException e) {
            throw new SessionException("Failed to append message to " + jsonlPath, e);
        } finally {
            writeLock.unlock();
        }
    }

    private void writeMeta() throws IOException {
        writeEvent(new SessionEvent.Meta(
            sessionId, createdAtMs, updatedAtMs, VERSION, workspaceRoot, model));
    }

    private void writeEvent(SessionEvent event) throws IOException {
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(new IOException(e));
        }
        Files.writeString(jsonlPath, json + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(jsonlPath)) return;
        if (Files.size(jsonlPath) < ROTATE_AFTER_BYTES) return;

        for (int i = MAX_ROTATED_FILES; i >= 1; i--) {
            Path src = rotatedPath(i);
            if (!Files.exists(src)) continue;
            if (i == MAX_ROTATED_FILES) {
                Files.delete(src);
            } else {
                Files.move(src, rotatedPath(i + 1));
            }
        }
        Files.move(jsonlPath, rotatedPath(1));
        writeMeta();
    }

    private Path rotatedPath(int n) {
        return jsonlPath.resolveSibling(jsonlPath.getFileName().toString() + "." + n);
    }
}
