package io.clawcode.cli;

import io.clawcode.api.AnthropicProviderClient;
import io.clawcode.api.InputMessage;
import io.clawcode.api.OpenAiCompatProviderClient;
import io.clawcode.api.ProviderClient;
import io.clawcode.core.MessageRole;
import io.clawcode.runtime.ConfigLoader;
import io.clawcode.runtime.ConversationMessage;
import io.clawcode.runtime.ConversationRuntime;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.PermissionPolicy;
import io.clawcode.runtime.PermissionPrompter;
import io.clawcode.runtime.RuntimeConfig;
import io.clawcode.runtime.Session;
import io.clawcode.runtime.SessionStore;
import io.clawcode.runtime.ToolContext;
import io.clawcode.tools.BuiltinToolRegistry;

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
    public final BuiltinToolRegistry tools;
    public final ToolContext toolCtx;
    public final SessionStore sessionStore;
    public final Session session;
    public final ConversationRuntime conversation;

    private RuntimeEnvironment(RuntimeConfig config, String model, int maxTokens,
                               PermissionMode permissionMode, PermissionPolicy permissions,
                               ProviderClient provider, BuiltinToolRegistry tools,
                               ToolContext toolCtx, SessionStore sessionStore, Session session,
                               ConversationRuntime conversation) {
        this.config = config;
        this.model = model;
        this.maxTokens = maxTokens;
        this.permissionMode = permissionMode;
        this.permissions = permissions;
        this.provider = provider;
        this.tools = tools;
        this.toolCtx = toolCtx;
        this.sessionStore = sessionStore;
        this.session = session;
        this.conversation = conversation;
    }

    public static RuntimeEnvironment bootstrap(Options opts, SessionStore sessionStore) {
        Path workingDir = Path.of("").toAbsolutePath();
        RuntimeConfig config = new ConfigLoader().load(workingDir);

        String resolvedModel = ModelAliases.resolve(firstNonNull(opts.model, config.model()));
        int resolvedMaxTokens = firstNonNull(opts.maxTokens, config.maxTokens(), 1024);

        PermissionMode mode = PermissionMode.fromCliName(firstNonNull(
            opts.permissionMode,
            config.permissions().defaultMode() != null
                ? config.permissions().defaultMode().cliName()
                : null,
            PermissionMode.WORKSPACE_WRITE.cliName()));

        BuiltinToolRegistry tools = new BuiltinToolRegistry();
        PermissionPolicy permissions = tools.applyRequirementsTo(new PermissionPolicy(mode)).build();
        HttpClient webHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        ToolContext toolCtx = new ToolContext(workingDir, permissions, PermissionPrompter.DENY_ALL, webHttp);

        ProviderClient provider = selectProvider();

        Session session = opts.resume != null
            ? sessionStore.load(opts.resume)
            : sessionStore.createNew(workingDir.toString(), resolvedModel);

        ConversationRuntime conversation = new ConversationRuntime(
            provider, tools, permissions, PermissionPrompter.DENY_ALL, toolCtx,
            resolvedModel, resolvedMaxTokens, null);
        session.messages().forEach(cm ->
            conversation.addHistory(new InputMessage(cm.role().wire(), cm.blocks())));

        return new RuntimeEnvironment(
            config, resolvedModel, resolvedMaxTokens, mode, permissions,
            provider, tools, toolCtx, sessionStore, session, conversation);
    }

    public void persistNewHistory(int historyBefore) {
        conversation.history()
            .subList(historyBefore, conversation.history().size())
            .forEach(msg -> session.append(new ConversationMessage(
                MessageRole.fromWire(msg.role()), msg.content(), null)));
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
}
