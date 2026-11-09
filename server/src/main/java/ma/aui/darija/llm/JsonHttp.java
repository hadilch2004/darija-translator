package ma.aui.darija.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Tiny JSON-over-HTTP helper shared by the Gemini and Ollama clients. */
final class JsonHttp {

    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long RETRY_DELAY_MILLIS = 700;

    private JsonHttp() {
    }

    /**
     * POSTs {@code body} as JSON and returns the parsed JSON answer. A single retry is made when the
     * provider says it is temporarily overloaded (HTTP 503), which the free Gemini tier does often.
     */
    static JsonNode postJson(HttpClient http, URI uri, Map<String, String> headers, JsonNode body, Duration timeout)
            throws LlmException {
        String payload;
        try {
            payload = MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw LlmException.network(e);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        HttpRequest request = builder.build();

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (HttpTimeoutException e) {
                throw LlmException.timeout(e);
            } catch (IOException e) {
                throw LlmException.network(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw LlmException.network(e);
            }

            int status = response.statusCode();
            if (status == 503 && attempt < 2) {
                sleep();
                continue;
            }
            if (status >= 200 && status < 300) {
                try {
                    return MAPPER.readTree(response.body());
                } catch (JsonProcessingException e) {
                    throw LlmException.upstream(502, "The LLM provider returned invalid JSON.");
                }
            }
            throw LlmException.upstream(status, errorMessage(response.body(), status));
        }
    }

    /** Extracts a short, human-readable message from a provider error body. */
    static String errorMessage(String body, int status) {
        String message = null;
        try {
            JsonNode error = MAPPER.readTree(body).path("error");
            message = error.isObject() ? error.path("message").asText(null) : error.asText(null);
        } catch (IOException | RuntimeException ignored) {
            // Body was not JSON; fall through to the generic message.
        }
        if (message == null || message.isBlank()) {
            message = "The LLM provider answered with HTTP " + status + ".";
        }
        return message.length() > 300 ? message.substring(0, 300) + "…" : message;
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
