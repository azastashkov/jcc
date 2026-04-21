package io.clawcode.cli;

import io.clawcode.api.AnthropicProviderClient;
import io.clawcode.api.InputMessage;
import io.clawcode.api.MessageRequest;
import io.clawcode.api.ProviderClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "prompt", description = "Send a one-shot prompt and stream the response.")
public final class PromptSubcommand implements Callable<Integer> {

    @Option(names = "--model", description = "Model name or alias (opus, sonnet, haiku).")
    String model = ModelAliases.DEFAULT_MODEL;

    @Option(names = "--output-format", description = "Output format: text or json.")
    String outputFormat = "text";

    @Option(names = "--max-tokens", description = "Maximum tokens in the response.")
    int maxTokens = 1024;

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

        MessageRequest request = MessageRequest.builder()
            .model(ModelAliases.resolve(model))
            .maxTokens(maxTokens)
            .messages(List.of(InputMessage.userText(prompt)))
            .build();

        try (StreamingRenderer r = renderer) {
            client.stream(request, new StreamEventBridge(r));
            return 0;
        } catch (RuntimeException e) {
            System.err.println("Request failed: " + e.getMessage());
            return 1;
        }
    }
}
