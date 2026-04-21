package io.jcc.runtime;

import java.net.http.HttpClient;
import java.nio.file.Path;

public record ToolContext(
    Path workingDir,
    PermissionPolicy permissions,
    PermissionPrompter prompter,
    HttpClient webHttp
) {}
