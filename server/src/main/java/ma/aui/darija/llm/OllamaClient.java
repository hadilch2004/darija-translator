package ma.aui.darija.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Local lightweight-LLM back end using an <a href="https://ollama.com">Ollama</a> server
 * ({@code POST /api/chat}). Lets the service run fully offline and free of charge.
 */
public final class OllamaClient implements LlmClient {

    private final HttpClient http;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;

    public OllamaClient(String baseUrl, String model, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public String translate(TranslationTask task) throws LlmException {
        ObjectNode body = JsonHttp.MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", Prompts.system(task));
        messages.addObject().put("role", "user").put("content", Prompts.user(task));
        body.putObject("options").put("temperature", 0.2);

        JsonNode answer = JsonHttp.postJson(http, URI.create(baseUrl + "/api/chat"), Map.of(), body, timeout);
        String text = answer.path("message").path("content").asText("");
        if (text.isBlank()) {
            throw LlmException.empty();
        }
        return text;
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String model() {
        return model;
    }
}
