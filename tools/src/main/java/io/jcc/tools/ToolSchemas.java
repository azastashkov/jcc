package io.jcc.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jcc.core.JsonMapper;

import java.util.List;

final class ToolSchemas {

    private ToolSchemas() {}

    static JsonNode object(String... propertyDefs) {
        ObjectNode root = JsonMapper.shared().createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        List<String> required = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < propertyDefs.length; i += 2) {
            String name = propertyDefs[i];
            String spec = propertyDefs[i + 1];
            boolean req = spec.endsWith("!");
            if (req) spec = spec.substring(0, spec.length() - 1);
            String[] parts = spec.split(":", 2);
            String type = parts[0];
            String description = parts.length > 1 ? parts[1] : "";
            ObjectNode prop = props.putObject(name);
            prop.put("type", type);
            if (!description.isEmpty()) {
                prop.put("description", description);
            }
            if (req) {
                required.add(name);
            }
        }
        if (!required.isEmpty()) {
            var arr = root.putArray("required");
            required.forEach(arr::add);
        }
        root.put("additionalProperties", false);
        return root;
    }
}
