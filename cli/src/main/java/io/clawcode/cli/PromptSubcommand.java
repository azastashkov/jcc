package io.clawcode.cli;

import io.clawcode.api.AnthropicProviderClient;
import io.clawcode.api.ProviderClient;
import io.clawcode.runtime.AssistantEventHandler;
import io.clawcode.runtime.ConversationRuntime;
import io.clawcode.runtime.PermissionMode;
import io.clawcode.runtime.PermissionPolicy;
import io.clawcode.runtime.PermissionPrompter;
import io.clawcode.runtime.ToolContext;
import io.clawcode.tools.BuiltinToolRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "prompt", description = "Send a one-shot prompt and stream the response.")
public final class PromptSubcommand implements Callable<Integer> {

    @Option(names = "--model", description = "Model name or alias (opus, sonnet, haiku).")
    String model = ModelAliases.DEFAULT_MODEL;

    @Option(names = "--output-format", description = "Output format: text or json.")
    String outputFormat = "text";

    @Option(names = "--max-tokens", description = "Maximum tokens in the response.")
    int maxTokens = 1024;

    @Option(
        names = "--permission-mode",
        description = "Permission mode: read-only, workspace-write, danger-full-access, prompt, allow. "
            + "Defaults to workspace-write.")
    String permissionMode = PermissionMode.WORKSPACE_WRITE.cliName();

    @Parameters(arity = "1", description = "The prompt text.")
    String prompt;

    @Override
    public Integer call() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ANTHROPIC_API_KEY environment variable is not set.");
            return 2;
        }

        ProviderClient client = new AnthropicProviderClient(apiKey);
        return runPrompt(client, System.out);
    }

    int runPrompt(ProviderClient client, PrintStream out) {
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

        PermissionMode mode;
        try {
            mode = PermissionMode.fromCliName(permissionMode);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        BuiltinToolRegistry tools = new BuiltinToolRegistry();
        PermissionPolicy permissions = tools.applyRequirementsTo(new PermissionPolicy(mode)).build();
        HttpClient webHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        ToolContext toolCtx = new ToolContext(
            Path.of("").toAbsolutePath(),
            permissions,
            PermissionPrompter.DENY_ALL,
            webHttp);

        ConversationRuntime conversation = new ConversationRuntime(
            client, tools, permissions, PermissionPrompter.DENY_ALL, toolCtx,
            ModelAliases.resolve(model), maxTokens, null);

        AssistantEventHandler handler = new RenderingAssistantHandler(renderer);
        try (StreamingRenderer r = renderer) {
            conversation.runTurn(prompt, handler);
            return 0;
        } catch (RuntimeException e) {
            System.err.println("Request failed: " + e.getMessage());
            return 1;
        }
    }
}
