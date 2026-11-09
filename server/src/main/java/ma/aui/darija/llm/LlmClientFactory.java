package ma.aui.darija.llm;

import java.time.Duration;
import ma.aui.darija.config.AppConfig;

/** Creates the {@link LlmClient} selected by {@code LLM_PROVIDER}. */
public final class LlmClientFactory {

    private LlmClientFactory() {
    }

    public static LlmClient create(AppConfig config) {
        Duration timeout = Duration.ofSeconds(config.llmTimeoutSeconds());
        return switch (config.provider()) {
            case "gemini" -> new GeminiClient(config.geminiApiKey(), config.geminiModel(), config.geminiBaseUrl(), timeout);
            case "ollama" -> new OllamaClient(config.ollamaUrl(), config.ollamaModel(), timeout);
            case "mock" -> new MockLlmClient();
            default -> throw new IllegalArgumentException("Unknown provider " + config.provider());
        };
    }
}
