package io.clawcode.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PermissionsConfig(
    PermissionMode defaultMode,
    List<String> allow,
    List<String> deny,
    List<String> ask
) {

    public PermissionsConfig {
        allow = allow == null ? List.of() : List.copyOf(allow);
        deny = deny == null ? List.of() : List.copyOf(deny);
        ask = ask == null ? List.of() : List.copyOf(ask);
    }

    public static PermissionsConfig empty() {
        return new PermissionsConfig(null, null, null, null);
    }
}
