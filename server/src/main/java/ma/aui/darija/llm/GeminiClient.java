package ma.aui.darija.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Google Gemini back end, using the public {@code generateContent} REST method.
 *
 * <p>The API key travels in the {@code x-goog-api-key} header (never in the URL) and is never logged.
 */
public final class GeminiClient implements LlmClient {

    private final HttpClient http;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;

    public GeminiClient(String apiKey, String model, String baseUrl, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
    }

    @Override
    public String translate(TranslationTask task) throws LlmException {
        ObjectNode body = JsonHttp.MAPPER.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", Prompts.system(task));
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", Prompts.user(task));
        body.putObject("generationConfig").put("temperature", 0.2);

        URI uri = URI.create(baseUrl + "/models/" + model + ":generateContent");
        JsonNode answer = JsonHttp.postJson(http, uri, Map.of("x-goog-api-key", apiKey), body, timeout);
        return extractText(answer);
    }

    /** Concatenates the text parts of the first candidate, or explains why there are none. */
    static String extractText(JsonNode answer) throws LlmException {
        JsonNode parts = answer.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            text.append(part.path("text").asText(""));
        }
        if (text.isEmpty()) {
            String blockReason = answer.path("promptFeedback").path("blockReason").asText("");
            if (!blockReason.isEmpty()) {
                throw LlmException.blocked(blockReason);
            }
            String finishReason = answer.path("candidates").path(0).path("finishReason").asText("");
            if (finishReason.equals("SAFETY") || finishReason.equals("PROHIBITED_CONTENT")) {
                throw LlmException.blocked(finishReason);
            }
            throw LlmException.empty();
        }
        return text.toString();
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public String model() {
        return model;
    }
}
