package io.jcc.cli;

import io.jcc.api.AnthropicProviderClient;
import io.jcc.api.InputMessage;
import io.jcc.api.OpenAiCompatProviderClient;
import io.jcc.api.ProviderClient;
import io.jcc.core.MessageRole;
import io.jcc.runtime.AssistantEventHandler;
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
import io.jcc.tools.BuiltinToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "prompt", description = "Send a one-shot prompt and stream the response.")
public final class PromptSubcommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(PromptSubcommand.class);

    @Option(names = "--model", description = "Model name or alias (opus, sonnet, haiku).")
    String model;

    @Option(names = "--output-format", description = "Output format: text or json.")
    String outputFormat = "text";

    @Option(names = "--max-tokens", description = "Maximum tokens in the response.")
    Integer maxTokens;

    @Option(
        names = "--permission-mode",
        description = "Permission mode: read-only, workspace-write, danger-full-access, prompt, allow.")
    String permissionMode;

    @Option(
        names = "--resume",
        description = "Resume a prior session by id, filename, 'latest', or an explicit path.")
    String resume;

    @Option(names = "--no-color", description = "Disable ANSI colors.")
    boolean noColor;

    @Parameters(arity = "1", description = "The prompt text.")
    String prompt;

    @Override
    public Integer call() {
        ProviderClient client;
        try {
            client = selectProvider();
        } catch (IllegalStateException e) {
            log.error("{}", e.getMessage());
            return 2;
        }
        return runPrompt(client, System.out, SessionStore.defaultStore());
    }

    private static ProviderClient selectProvider() {
        String openAiBase = System.getenv("OPENAI_BASE_URL");
        if (openAiBase != null && !openAiBase.isBlank()) {
            String key = System.getenv("OPENAI_API_KEY");
            return new OpenAiCompatProviderClient(key == null ? "" : key, URI.create(openAiBase));
        }
        String anthropic = System.getenv("ANTHROPIC_API_KEY");
        if (anthropic == null || anthropic.isBlank()) {
            throw new IllegalStateException(
                "ANTHROPIC_API_KEY is not set (or set OPENAI_BASE_URL + OPENAI_API_KEY for an OpenAI-compatible endpoint).");
        }
        return new AnthropicProviderClient(anthropic);
    }

    int runPrompt(ProviderClient client, PrintStream out, SessionStore store) {
        Style style = noColor ? Style.PLAIN : Style.detect();
        StreamingRenderer renderer = switch (outputFormat) {
            case "text" -> new TextRenderer(out, style);
            case "json" -> new JsonRenderer(out);
            default -> {
                log.error("Unsupported --output-format: {}", outputFormat);
                yield null;
            }
        };
        if (renderer == null) {
            return 2;
        }

        Path workingDir = Path.of("").toAbsolutePath();
        RuntimeConfig config = new ConfigLoader().load(workingDir);

        String resolvedModel = ModelAliases.resolve(firstNonNull(model, config.model()));
        int resolvedMaxTokens = firstNonNull(maxTokens, config.maxTokens(), 8192);

        PermissionMode mode;
        try {
            String modeName = firstNonNull(
                permissionMode,
                config.permissions().defaultMode() != null
                    ? config.permissions().defaultMode().cliName()
                    : null,
                PermissionMode.WORKSPACE_WRITE.cliName());
            mode = PermissionMode.fromCliName(modeName);
        } catch (IllegalArgumentException e) {
            log.error("{}", e.getMessage());
            return 2;
        }

        BuiltinToolRegistry tools = new BuiltinToolRegistry();
        PermissionPolicy permissions = tools.applyRequirementsTo(new PermissionPolicy(mode)).build();
        HttpClient webHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        ToolContext toolCtx = new ToolContext(workingDir, permissions, PermissionPrompter.DENY_ALL, webHttp);

        Session session = resume != null
            ? store.load(resume)
            : store.createNew(workingDir.toString(), resolvedModel);

        ConversationRuntime conversation = new ConversationRuntime(
            client, tools, permissions, PermissionPrompter.DENY_ALL, toolCtx,
            resolvedModel, resolvedMaxTokens, null);
        session.messages().forEach(cm ->
            conversation.addHistory(new InputMessage(cm.role().wire(), cm.blocks())));

        AssistantEventHandler handler = new RenderingAssistantHandler(renderer);
        int historyBefore = conversation.history().size();

        try (StreamingRenderer r = renderer) {
            conversation.runTurn(prompt, handler);
        } catch (RuntimeException e) {
            log.error("Request failed: {}", e.getMessage(), e);
            return 1;
        }

        List<InputMessage> newMessages = conversation.history()
            .subList(historyBefore, conversation.history().size());
        for (InputMessage msg : newMessages) {
            session.append(new ConversationMessage(
                MessageRole.fromWire(msg.role()), msg.content(), null));
        }
        return 0;
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
