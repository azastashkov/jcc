package io.clawcode.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void returnsEmptyWhenNoFilesExist(@TempDir Path dir) {
        RuntimeConfig cfg = new ConfigLoader().load(dir);
        assertThat(cfg.model()).isNull();
        assertThat(cfg.permissions()).isNotNull();
    }

    @Test
    void projectConfigProvidesDefaults(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".claw.json"), """
            {
              "model": "sonnet",
              "max_tokens": 2048,
              "permissions": {
                "default_mode": "workspace-write",
                "allow": ["read_file", "glob"],
                "deny": ["bash"]
              }
            }
            """);
        RuntimeConfig cfg = new ConfigLoader().load(dir);
        assertThat(cfg.model()).isEqualTo("sonnet");
        assertThat(cfg.maxTokens()).isEqualTo(2048);
        assertThat(cfg.permissions().defaultMode()).isEqualTo(PermissionMode.WORKSPACE_WRITE);
        assertThat(cfg.permissions().allow()).containsExactly("read_file", "glob");
        assertThat(cfg.permissions().deny()).containsExactly("bash");
    }

    @Test
    void localOverrideReplacesScalarButMergesAllowList(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".claw.json"), """
            {
              "model": "sonnet",
              "permissions": {
                "default_mode": "workspace-write",
                "allow": ["read_file"]
              }
            }
            """);
        Files.createDirectories(dir.resolve(".claw"));
        Files.writeString(dir.resolve(".claw/settings.local.json"), """
            {
              "model": "opus",
              "permissions": {
                "allow": ["grep"]
              }
            }
            """);
        RuntimeConfig cfg = new ConfigLoader().load(dir);
        assertThat(cfg.model()).isEqualTo("opus");
        assertThat(cfg.permissions().defaultMode()).isEqualTo(PermissionMode.WORKSPACE_WRITE);
        assertThat(cfg.permissions().allow()).containsExactly("read_file", "grep");
    }

    @Test
    void malformedJsonSurfacesClearError(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".claw.json"), "{ not json");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ConfigLoader().load(dir))
            .isInstanceOf(ConfigException.class);
    }
}
