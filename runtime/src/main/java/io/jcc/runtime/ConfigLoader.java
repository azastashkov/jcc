package io.jcc.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jcc.core.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConfigLoader {

    private static final Set<String> ADDITIVE_ARRAY_PATHS = Set.of(
        "permissions.allow",
        "permissions.deny",
        "permissions.ask");

    private final ObjectMapper mapper = JsonMapper.shared();

    public RuntimeConfig load(Path workspaceRoot) {
        List<Path> candidates = List.of(
            homeDir().resolve(".claude").resolve("settings.json"),
            workspaceRoot.resolve(".jcc.json"),
            workspaceRoot.resolve(".jcc").resolve("settings.json"),
            workspaceRoot.resolve(".jcc").resolve("settings.local.json"));

        ObjectNode merged = mapper.createObjectNode();
        for (Path p : candidates) {
            if (!Files.exists(p)) continue;
            try {
                JsonNode layer = mapper.readTree(Files.readString(p));
                if (!(layer instanceof ObjectNode layerObj)) {
                    throw new ConfigException("Config at " + p + " must be a JSON object");
                }
                mergeInto(merged, layerObj, "");
            } catch (IOException e) {
                throw new ConfigException("Failed to read config at " + p, e);
            }
        }

        try {
            return mapper.treeToValue(merged, RuntimeConfig.class);
        } catch (IOException e) {
            throw new ConfigException("Invalid merged config: " + merged, e);
        }
    }

    private Path homeDir() {
        return Path.of(System.getProperty("user.home"));
    }

    void mergeInto(ObjectNode target, ObjectNode source, String path) {
        Iterator<String> names = source.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            JsonNode incoming = source.get(field);
            String childPath = path.isEmpty() ? field : path + "." + field;
            JsonNode existing = target.get(field);

            if (incoming == null || incoming.isNull()) {
                target.set(field, incoming);
            } else if (incoming.isObject() && existing instanceof ObjectNode existingObj) {
                mergeInto(existingObj, (ObjectNode) incoming, childPath);
            } else if (incoming.isArray() && existing instanceof ArrayNode existingArr
                    && ADDITIVE_ARRAY_PATHS.contains(childPath)) {
                Set<String> seen = new LinkedHashSet<>();
                existingArr.forEach(n -> seen.add(n.asText()));
                for (JsonNode v : incoming) {
                    if (seen.add(v.asText())) existingArr.add(v);
                }
            } else {
                target.set(field, incoming.deepCopy());
            }
        }
    }
}
