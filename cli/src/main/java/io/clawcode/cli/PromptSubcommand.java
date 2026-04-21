package io.clawcode.cli;

import io.clawcode.api.AnthropicProviderClient;
import io.clawcode.api.InputMessage;
import io.clawcode.api.ProviderClient;
import io.clawcode.core.MessageRole;
import io.clawcode.runtime.AssistantEventHandler;
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
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "prompt", description = "Send a one-shot prompt and stream the response.")
public final class PromptSubcommand implements Callable<Integer> {

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

    @Parameters(arity = "1", description = "The prompt text.")
    String prompt;

    @Override
    public Integer call() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ANTHROPIC_API_KEY environment variable is not set.");
            return 2;
        }
        return runPrompt(new AnthropicProviderClient(apiKey), System.out, SessionStore.defaultStore());
    }

    int runPrompt(ProviderClient client, PrintStream out, SessionStore store) {
        StreamingRenderer renderer = switch (outputFormat) {
            case "text" -> new TextRenderer(out);
            case "json" -> new JsonRenderer(out);
            default -> {
                System.err.println("Unsupported --output-format: " + outputFormat);
                yield null;
            }
        };
        if (renderer == null) {
            return 2;
        }

        Path workingDir = Path.of("").toAbsolutePath();
        RuntimeConfig config = new ConfigLoader().load(workingDir);

        String resolvedModel = ModelAliases.resolve(firstNonNull(model, config.model()));
        int resolvedMaxTokens = firstNonNull(maxTokens, config.maxTokens(), 1024);

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
            System.err.println(e.getMessage());
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
            System.err.println("Request failed: " + e.getMessage());
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
