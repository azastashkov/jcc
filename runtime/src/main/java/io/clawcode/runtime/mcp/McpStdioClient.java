package io.clawcode.runtime.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.clawcode.core.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class McpStdioClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpStdioClient.class);
    private static final long DEFAULT_INITIALIZE_TIMEOUT_MS = 10_000;
    private static final long DEFAULT_LIST_TOOLS_TIMEOUT_MS = 30_000;
    private static final long DEFAULT_CALL_TIMEOUT_MS = 120_000;

    private final String name;
    private final McpServerConfig config;
    private final Process process;
    private final BufferedReader stdout;
    private final BufferedWriter stdin;
    private final ObjectMapper mapper = JsonMapper.shared();
    private final ExecutorService executor;
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    McpStdioClient(String name, McpServerConfig config, Process process, ExecutorService executor) {
        this.name = name;
        this.config = config;
        this.process = process;
        this.executor = executor;
        this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        startReaders();
    }

    public static McpStdioClient spawn(String name, McpServerConfig config, ExecutorService executor) {
        Objects.requireNonNull(config.command(), "stdio MCP server requires 'command'");
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(config.command());
        pb.command().addAll(config.args());
        if (!config.env().isEmpty()) {
            pb.environment().putAll(config.env());
        }
        pb.redirectErrorStream(false);
        try {
            Process process = pb.start();
            return new McpStdioClient(name, config, process, executor);
        } catch (IOException e) {
            throw new McpTransportException(
                "Failed to spawn MCP server '" + name + "' (" + config.command() + "): " + e.getMessage(), e);
        }
    }

    public String name() {
        return name;
    }

    public JsonNode initialize() {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.putObject("capabilities");
        ObjectNode info = params.putObject("clientInfo");
        info.put("name", "clawcode-java");
        info.put("version", "0.1.0");
        JsonNode result = request("initialize", params,
            config.initializeTimeoutMsOr(DEFAULT_INITIALIZE_TIMEOUT_MS));
        notifyNoResponse("notifications/initialized", mapper.createObjectNode());
        return result;
    }

    public JsonNode listTools() {
        return request("tools/list", mapper.createObjectNode(),
            config.toolsListTimeoutMsOr(DEFAULT_LIST_TOOLS_TIMEOUT_MS));
    }

    public JsonNode callTool(String toolName, JsonNode arguments) {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", arguments == null ? mapper.createObjectNode() : arguments);
        return request("tools/call", params, config.toolCallTimeoutMsOr(DEFAULT_CALL_TIMEOUT_MS));
    }

    private JsonNode request(String method, JsonNode params, long timeoutMs) {
        if (shutdown.get()) {
            throw new McpTransportException("MCP client '" + name + "' is shut down");
        }
        String id = Long.toString(nextId.getAndIncrement());
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("method", method);
        envelope.set("params", params);

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            writeLine(mapper.writeValueAsString(envelope));
        } catch (IOException e) {
            pending.remove(id);
            throw new McpTransportException(
                "Failed to write request '" + method + "' to MCP server '" + name + "'", e);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpTransportException("Interrupted waiting for MCP response", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new McpTransportException(
                "MCP '" + name + "' method '" + method + "' failed: " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new McpTransportException(
                "MCP '" + name + "' method '" + method + "' timed out after " + timeoutMs + "ms");
        }
    }

    private void notifyNoResponse(String method, JsonNode params) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.put("method", method);
        envelope.set("params", params);
        try {
            writeLine(mapper.writeValueAsString(envelope));
        } catch (IOException e) {
            log.warn("Failed to write notification {} to MCP '{}'", method, name, e);
        }
    }

    private void writeLine(String line) throws IOException {
        writeLock.lock();
        try {
            stdin.write(line);
            stdin.write('\n');
            stdin.flush();
        } finally {
            writeLock.unlock();
        }
    }

    private void startReaders() {
        executor.submit(this::drainStdout);
        executor.submit(this::drainStderr);
    }

    private void drainStdout() {
        try {
            String line;
            while (!shutdown.get() && (line = stdout.readLine()) != null) {
                try {
                    JsonNode msg = mapper.readTree(line);
                    if (msg.has("id") && !msg.get("id").isNull()) {
                        String id = msg.get("id").asText();
                        CompletableFuture<JsonNode> future = pending.remove(id);
                        if (future != null) {
                            if (msg.hasNonNull("error")) {
                                future.completeExceptionally(new McpTransportException(
                                    "MCP error: " + msg.get("error").toString()));
                            } else {
                                future.complete(msg.path("result"));
                            }
                        } else {
                            log.debug("MCP '{}' unmatched response id={} line={}", name, id, line);
                        }
                    } else {
                        log.debug("MCP '{}' notification: {}", name, line);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("MCP '{}' malformed stdout line: {}", name, line);
                }
            }
        } catch (IOException e) {
            if (!shutdown.get()) {
                log.warn("MCP '{}' stdout reader terminated: {}", name, e.getMessage());
            }
        } finally {
            failPending(new McpTransportException("MCP '" + name + "' stream closed"));
        }
    }

    private void drainStderr() {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!shutdown.get() && (line = reader.readLine()) != null) {
                log.debug("MCP '{}' stderr: {}", name, line);
            }
        } catch (IOException ignored) {
        }
    }

    private void failPending(Throwable cause) {
        pending.values().forEach(future -> future.completeExceptionally(cause));
        pending.clear();
    }

    @Override
    public void close() {
        if (!shutdown.compareAndSet(false, true)) return;
        failPending(new McpTransportException("MCP '" + name + "' shut down"));
        try {
            stdin.close();
        } catch (IOException ignored) {
        }
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
