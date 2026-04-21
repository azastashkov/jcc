package io.jcc.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class SessionStore {

    private final Path sessionsDir;

    public SessionStore(Path sessionsDir) {
        this.sessionsDir = sessionsDir;
    }

    public static SessionStore defaultStore() {
        String home = System.getProperty("user.home");
        return new SessionStore(Path.of(home, ".local", "share", "jcc", "sessions"));
    }

    public Path sessionsDir() {
        return sessionsDir;
    }

    public Session createNew(String workspaceRoot, String model) {
        ensureDir();
        long now = System.currentTimeMillis();
        Path path = sessionsDir.resolve("session-" + now + ".jsonl");
        return Session.create(path, workspaceRoot, model);
    }

    public Session load(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new SessionException("session reference must not be blank");
        }
        Path p = Path.of(reference);
        if (Files.exists(p) && Files.isRegularFile(p)) {
            return Session.load(p);
        }
        if ("latest".equals(reference)) {
            return loadLatest();
        }
        Path candidate = sessionsDir.resolve(reference.endsWith(".jsonl") ? reference : reference + ".jsonl");
        if (Files.exists(candidate)) {
            return Session.load(candidate);
        }
        throw new SessionException("Session not found: " + reference);
    }

    public Session loadLatest() {
        ensureDir();
        try (Stream<Path> stream = Files.list(sessionsDir)) {
            Path latest = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                .max(Comparator.comparingLong(SessionStore::lastModified))
                .orElseThrow(() -> new SessionException("No sessions exist yet in " + sessionsDir));
            return Session.load(latest);
        } catch (IOException e) {
            throw new SessionException("Failed to list sessions in " + sessionsDir, e);
        }
    }

    public List<Path> list() {
        ensureDir();
        try (Stream<Path> stream = Files.list(sessionsDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                .sorted(Comparator.comparingLong(SessionStore::lastModified).reversed())
                .toList();
        } catch (IOException e) {
            throw new SessionException("Failed to list sessions", e);
        }
    }

    private void ensureDir() {
        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            throw new SessionException("Failed to create sessions dir " + sessionsDir, e);
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ignored) {
            return 0;
        }
    }
}
