package io.clawcode.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clawcode.api.ToolDefinition;
import io.clawcode.core.JsonMapper;
import io.clawcode.core.Result;
import io.clawcode.runtime.ToolError;
import io.clawcode.runtime.ToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public final class McpServerManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpServerManager.class);
    private static final String TOOL_PREFIX = "mcp__";

    private final Map<String, McpStdioClient> clients = new LinkedHashMap<>();
    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private final ObjectMapper mapper = JsonMapper.shared();

    private McpServerManager() {}

    public static McpServerManager startAll(Map<String, McpServerConfig> configs, ExecutorService executor) {
        McpServerManager mgr = new McpServerManager();
        for (Map.Entry<String, McpServerConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            McpServerConfig config = entry.getValue();
            if (config.type() != null && !"stdio".equalsIgnoreCase(config.type())) {
                log.warn("MCP server '{}' has unsupported type '{}' — skipping (only 'stdio' is supported in MVP).",
                    name, config.type());
                continue;
            }
            try {
                McpStdioClient client = McpStdioClient.spawn(name, config, executor);
                client.initialize();
                JsonNode list = client.listTools();
                JsonNode toolsArray = list.path("tools");
                if (toolsArray.isArray()) {
                    for (JsonNode t : toolsArray) {
                        McpTool tool = new McpTool(
                            name,
                            t.path("name").asText(),
                            t.path("description").asText(null),
                            t.path("inputSchema"));
                        mgr.tools.put(tool.qualifiedName(), tool);
                    }
                }
                mgr.clients.put(name, client);
            } catch (McpTransportException e) {
                log.warn("Failed to start MCP server '{}': {}", name, e.getMessage());
            }
        }
        return mgr;
    }

    public List<ToolDefinition> toolDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (McpTool tool : tools.values()) {
            defs.add(new ToolDefinition(tool.qualifiedName(), tool.description(), tool.inputSchema()));
        }
        return defs;
    }

    public Collection<McpTool> toolList() {
        return List.copyOf(tools.values());
    }

    public Collection<String> serverNames() {
        return List.copyOf(clients.keySet());
    }

    public boolean handles(String toolName) {
        return toolName != null && toolName.startsWith(TOOL_PREFIX) && tools.containsKey(toolName);
    }

    public Result<ToolOutput, ToolError> execute(String toolName, JsonNode input) {
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            return Result.err(ToolError.of("MCP tool not registered: " + toolName));
        }
        McpStdioClient client = clients.get(tool.serverName());
        if (client == null) {
            return Result.err(ToolError.of("MCP server '" + tool.serverName() + "' is not connected"));
        }
        try {
            JsonNode result = client.callTool(tool.toolName(), input);
            return Result.ok(interpret(result));
        } catch (McpTransportException e) {
            return Result.err(ToolError.of(e.getMessage()));
        }
    }

    private ToolOutput interpret(JsonNode result) {
        JsonNode content = result.path("content");
        if (content.isArray() && !content.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : content) {
                String type = item.path("type").asText();
                if ("text".equals(type)) {
                    sb.append(item.path("text").asText());
                } else if ("resource".equals(type)) {
                    sb.append("[resource] ").append(item.path("resource").toString());
                }
                sb.append('\n');
            }
            return new ToolOutput.Text(sb.toString().stripTrailing());
        }
        return new ToolOutput.Structured(result);
    }

    @Override
    public void close() {
        for (McpStdioClient client : clients.values()) {
            try {
                client.close();
            } catch (RuntimeException e) {
                log.warn("Failed to close MCP client '{}': {}", client.name(), e.getMessage());
            }
        }
        clients.clear();
        tools.clear();
    }
}
