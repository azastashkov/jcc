package io.clawcode.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeConfig(
    String model,
    Integer maxTokens,
    PermissionsConfig permissions
) {

    public RuntimeConfig {
        permissions = permissions == null ? PermissionsConfig.empty() : permissions;
    }

    public static RuntimeConfig empty() {
        return new RuntimeConfig(null, null, PermissionsConfig.empty());
    }
}
