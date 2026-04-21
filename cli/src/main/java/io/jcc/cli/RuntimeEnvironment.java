package io.jcc.cli;

import io.jcc.api.AnthropicProviderClient;
import io.jcc.api.InputMessage;
import io.jcc.api.OpenAiCompatProviderClient;
import io.jcc.api.ProviderClient;
import io.jcc.core.Concurrency;
import io.jcc.core.MessageRole;
import io.jcc.runtime.CompositeToolExecutor;
import io.jcc.runtime.ConfigLoader;
import io.jcc.runtime.ConversationMessage;
import io.jcc.runtime.ConversationRuntime;
import io.jcc.runtime.PermissionMode;
import io.jcc.runtime.PermissionPolicy;
import io.jcc.runtime.PermissionPrompter;
import io.jcc.runtime.RuntimeConfig;
import io.jcc.runtime.Session;
import io.jcc.runtime.SessionStore;
import io.jcc.runtime.ToolContext;
import io.jcc.runtime.ToolExecutor;
import io.jcc.runtime.mcp.McpServerManager;
import io.jcc.runtime.subagent.SubagentExecutor;
import io.jcc.runtime.subagent.TaskRegistry;
import io.jcc.tools.BuiltinToolRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

public final class RuntimeEnvironment {

    public final RuntimeConfig config;
    public final String model;
    public final int maxTokens;
    public final PermissionMode permissionMode;
    public final PermissionPolicy permissions;
    public final ProviderClient provider;
    public final ToolExecutor toolExecutor;
    public final ToolContext toolCtx;
    public final SessionStore sessionStore;
    public final Session session;
    public final ConversationRuntime conversation;
    public final McpServerManager mcp;
    public final TaskRegistry tasks;
    public final Concurrency concurrency;

    private RuntimeEnvironment(Builder b) {
        this.config = b.config;
        this.model = b.model;
        this.maxTokens = b.maxTokens;
        this.permissionMode = b.permissionMode;
        this.permissions = b.permissions;
        this.provider = b.provider;
        this.toolExecutor = b.toolExecutor;
        this.toolCtx = b.toolCtx;
        this.sessionStore = b.sessionStore;
        this.session = b.session;
        this.conversation = b.conversation;
        this.mcp = b.mcp;
        this.tasks = b.tasks;
        this.concurrency = b.concurrency;
    }

    public static RuntimeEnvironment bootstrap(Options opts, SessionStore sessionStore) {
        Path workingDir = Path.of("").toAbsolutePath();
        RuntimeConfig config = new ConfigLoader().load(workingDir);

        String resolvedModel = ModelAliases.resolve(firstNonNull(opts.model, config.model()));
        int resolvedMaxTokens = firstNonNull(opts.maxTokens, config.maxTokens(), 8192);

        PermissionMode mode = PermissionMode.fromCliName(firstNonNull(
            opts.permissionMode,
            config.permissions().defaultMode() != null
                ? config.permissions().defaultMode().cliName()
                : null,
            PermissionMode.WORKSPACE_WRITE.cliName()));

        BuiltinToolRegistry builtinForPermissions = new BuiltinToolRegistry();
        PermissionPolicy permissions = builtinForPermissions
            .applyRequirementsTo(new PermissionPolicy(mode))
            .build()
            .withToolRequirement("Agent", PermissionMode.DANGER_FULL_ACCESS);
        HttpClient webHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        ToolContext toolCtx = new ToolContext(workingDir, permissions, PermissionPrompter.DENY_ALL, webHttp);

        ProviderClient provider = selectProvider();

        Concurrency concurrency = new Concurrency();
        McpServerManager mcp = config.mcp().isEmpty()
            ? null
            : McpServerManager.startAll(config.mcp(), concurrency.virtualThreads());

        TaskRegistry tasks = new TaskRegistry();
        BuiltinToolRegistry baseTools = new BuiltinToolRegistry();
        ToolExecutor parentExec = mcp != null
            ? new CompositeToolExecutor(baseTools, mcp)
            : baseTools;
        SubagentExecutor subagentExec = new SubagentExecutor(
            provider, parentExec, permissions, PermissionPrompter.DENY_ALL,
            toolCtx, resolvedModel, resolvedMaxTokens, tasks);

        BuiltinToolRegistry builtinsWithAgent = new BuiltinToolRegistry(
            BuiltinToolRegistry.withAgent(subagentExec));
        ToolExecutor toolExecutor = mcp != null
            ? new CompositeToolExecutor(builtinsWithAgent, mcp)
            : builtinsWithAgent;

        Session session = opts.resume != null
            ? sessionStore.load(opts.resume)
            : sessionStore.createNew(workingDir.toString(), resolvedModel);

        ConversationRuntime conversation = new ConversationRuntime(
            provider, toolExecutor, permissions, PermissionPrompter.DENY_ALL, toolCtx,
            resolvedModel, resolvedMaxTokens, null);
        session.messages().forEach(cm ->
            conversation.addHistory(new InputMessage(cm.role().wire(), cm.blocks())));

        Builder b = new Builder();
        b.config = config;
        b.model = resolvedModel;
        b.maxTokens = resolvedMaxTokens;
        b.permissionMode = mode;
        b.permissions = permissions;
        b.provider = provider;
        b.toolExecutor = toolExecutor;
        b.toolCtx = toolCtx;
        b.sessionStore = sessionStore;
        b.session = session;
        b.conversation = conversation;
        b.mcp = mcp;
        b.tasks = tasks;
        b.concurrency = concurrency;
        return new RuntimeEnvironment(b);
    }

    public void persistNewHistory(int historyBefore) {
        conversation.history()
            .subList(historyBefore, conversation.history().size())
            .forEach(msg -> session.append(new ConversationMessage(
                MessageRole.fromWire(msg.role()), msg.content(), null)));
    }

    public void close() {
        if (mcp != null) mcp.close();
        if (concurrency != null) concurrency.close();
    }

    public record Options(String model, Integer maxTokens, String permissionMode, String resume) {}

    private static ProviderClient selectProvider() {
        String openAiBase = System.getenv("OPENAI_BASE_URL");
        if (openAiBase != null && !openAiBase.isBlank()) {
            String key = firstNonBlank(System.getenv("OPENAI_API_KEY"), "");
            return new OpenAiCompatProviderClient(key, URI.create(openAiBase));
        }
        String anthropic = System.getenv("ANTHROPIC_API_KEY");
        if (anthropic == null || anthropic.isBlank()) {
            throw new IllegalStateException(
                "ANTHROPIC_API_KEY environment variable is not set "
                    + "(or set OPENAI_BASE_URL + OPENAI_API_KEY for an OpenAI-compatible endpoint).");
        }
        return new AnthropicProviderClient(anthropic);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static int firstNonNull(Integer... values) {
        for (Integer v : values) {
            if (v != null) return v;
        }
        throw new IllegalStateException("no non-null integer provided");
    }

    private static final class Builder {
        RuntimeConfig config;
        String model;
        int maxTokens;
        PermissionMode permissionMode;
        PermissionPolicy permissions;
        ProviderClient provider;
        ToolExecutor toolExecutor;
        ToolContext toolCtx;
        SessionStore sessionStore;
        Session session;
        ConversationRuntime conversation;
        McpServerManager mcp;
        TaskRegistry tasks;
        Concurrency concurrency;
    }
}
