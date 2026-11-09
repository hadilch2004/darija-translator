package ma.aui.darija;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import ma.aui.darija.llm.GeminiClient;
import ma.aui.darija.llm.LlmException;
import ma.aui.darija.llm.OllamaClient;
import ma.aui.darija.llm.Script;
import ma.aui.darija.llm.TranslationTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the real HTTP clients against a throw-away fake of the Gemini / Ollama endpoints. */
class GeminiClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TranslationTask TASK = new TranslationTask("Hello", "English", Script.ARABIC);

    private HttpServer fake;
    private String baseUrl;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> apiKeys = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startFake() throws IOException {
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.start();
        baseUrl = "http://127.0.0.1:" + fake.getAddress().getPort();
    }

    @AfterEach
    void stopFake() {
        fake.stop(0);
    }

    private void respond(String path, int status, String body) {
        fake.createContext(path, exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            apiKeys.add(String.valueOf(exchange.getRequestHeaders().getFirst("x-goog-api-key")));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private GeminiClient gemini() {
        return new GeminiClient("test-key", "gemini-test", baseUrl, Duration.ofSeconds(5));
    }

    @Test
    void sendsDocumentedRequestAndParsesAnswer() throws Exception {
        respond("/models/gemini-test:generateContent", 200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"السلام\"}]}}]}");

        String result = gemini().translate(TASK);

        assertEquals("السلام", result);
        assertEquals("/models/gemini-test:generateContent", paths.get(0));
        assertEquals("test-key", apiKeys.get(0));
        JsonNode body = JSON.readTree(bodies.get(0));
        assertTrue(body.at("/systemInstruction/parts/0/text").asText().contains("Moroccan"));
        assertEquals("user", body.at("/contents/0/role").asText());
        assertTrue(body.at("/contents/0/parts/0/text").asText().contains("Hello"));
        assertEquals(0.2, body.at("/generationConfig/temperature").asDouble(), 1e-9);
    }

    @Test
    void mapsQuotaErrorToUpstream429() {
        respond("/models/gemini-test:generateContent", 429,
                "{\"error\":{\"code\":429,\"message\":\"Quota exceeded\",\"status\":\"RESOURCE_EXHAUSTED\"}}");

        LlmException e = assertThrows(LlmException.class, () -> gemini().translate(TASK));

        assertEquals(LlmException.Kind.UPSTREAM, e.kind());
        assertEquals(429, e.upstreamStatus());
        assertEquals("Quota exceeded", e.getMessage());
    }

    @Test
    void retriesOnceWhenProviderIsOverloaded() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        fake.createContext("/models/gemini-test:generateContent", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean first = calls.incrementAndGet() == 1;
            byte[] bytes = (first
                    ? "{\"error\":{\"message\":\"overloaded\"}}"
                    : "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"salam\"}]}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(first ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        assertEquals("salam", gemini().translate(TASK));
        assertEquals(2, calls.get());
    }

    @Test
    void reportsPromptBlockedBySafetyFilters() {
        respond("/models/gemini-test:generateContent", 200,
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        LlmException e = assertThrows(LlmException.class, () -> gemini().translate(TASK));

        assertEquals(LlmException.Kind.BLOCKED, e.kind());
    }

    @Test
    void reportsEmptyAnswer() {
        respond("/models/gemini-test:generateContent", 200, "{\"candidates\":[]}");

        LlmException e = assertThrows(LlmException.class, () -> gemini().translate(TASK));

        assertEquals(LlmException.Kind.EMPTY, e.kind());
    }

    @Test
    void reportsUnreachableProvider() {
        fake.stop(0);

        LlmException e = assertThrows(LlmException.class, () -> gemini().translate(TASK));

        assertEquals(LlmException.Kind.NETWORK, e.kind());
    }

    @Test
    void ollamaClientUsesChatEndpoint() throws Exception {
        respond("/api/chat", 200, "{\"message\":{\"role\":\"assistant\",\"content\":\"salam\"}}");

        String result = new OllamaClient(baseUrl, "llama-test", Duration.ofSeconds(5)).translate(TASK);

        assertEquals("salam", result);
        JsonNode body = JSON.readTree(bodies.get(0));
        assertEquals("llama-test", body.path("model").asText());
        assertEquals(false, body.path("stream").asBoolean(true));
        assertEquals("system", body.at("/messages/0/role").asText());
        assertEquals("user", body.at("/messages/1/role").asText());
    }
}
