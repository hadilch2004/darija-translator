package ma.aui.darija;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import ma.aui.darija.config.AppConfig;
import ma.aui.darija.llm.LlmClient;
import ma.aui.darija.llm.LlmException;
import ma.aui.darija.llm.MockLlmClient;
import ma.aui.darija.llm.TranslationTask;
import org.glassfish.grizzly.http.server.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Boots the real Grizzly + Jersey stack on a free port and talks to it over HTTP. */
class ApiIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USER = "tester";
    private static final String PASSWORD = "pässword-123";

    private static HttpServer server;
    private static String base;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Behaves like the mock unless the text asks for a specific failure. */
    private static final LlmClient LLM = new LlmClient() {
        private final MockLlmClient mock = new MockLlmClient();

        @Override
        public String translate(TranslationTask task) throws LlmException {
            return switch (task.text()) {
                case "boom-429" -> throw LlmException.upstream(429, "Quota exceeded");
                case "boom-404" -> throw LlmException.upstream(404, "model not found");
                case "boom-timeout" -> throw LlmException.timeout(null);
                case "boom-unexpected" -> throw new IllegalStateException("secret internal detail");
                default -> mock.translate(task);
            };
        }

        @Override
        public String provider() {
            return "mock";
        }

        @Override
        public String model() {
            return "mock";
        }
    };

    @BeforeAll
    static void start() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        AppConfig config = AppConfig.from(Map.of(
                "LLM_PROVIDER", "mock",
                "HOST", "127.0.0.1",
                "PORT", Integer.toString(port),
                "API_USERNAME", USER,
                "API_PASSWORD", PASSWORD,
                "MAX_TEXT_LENGTH", "100")::get);
        server = DarijaServer.start(config, LLM);
        base = "http://127.0.0.1:" + port + "/api";
    }

    @AfterAll
    static void stop() {
        server.shutdownNow();
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> post(String json, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/translate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return send(request);
    }

    private static HttpResponse<String> postAuthed(String json) throws Exception {
        return post(json, basic(USER, PASSWORD));
    }

    @Test
    void healthIsPublic() throws Exception {
        HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(base + "/health")));
        assertEquals(200, r.statusCode());
        JsonNode body = JSON.readTree(r.body());
        assertEquals("UP", body.path("status").asText());
        assertEquals("mock", body.path("provider").asText());
    }

    @Test
    void translateRequiresCredentials() throws Exception {
        HttpResponse<String> r = post("{\"text\":\"Hello\"}", null);
        assertEquals(401, r.statusCode());
        assertTrue(r.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Basic realm="));
        assertEquals("unauthorized", JSON.readTree(r.body()).path("error").asText());
    }

    @Test
    void wrongPasswordUnknownUserAndGarbageAreRejected() throws Exception {
        assertEquals(401, post("{\"text\":\"Hello\"}", basic(USER, "wrong")).statusCode());
        assertEquals(401, post("{\"text\":\"Hello\"}", basic("nobody", PASSWORD)).statusCode());
        assertEquals(401, post("{\"text\":\"Hello\"}", "Basic !!!not-base64!!!").statusCode());
        assertEquals(401, post("{\"text\":\"Hello\"}", "Bearer abc").statusCode());
    }

    @Test
    void translatesWithValidCredentialsIncludingNonAsciiPassword() throws Exception {
        HttpResponse<String> r = postAuthed("{\"text\":\"Hello\",\"sourceLanguage\":\"English\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        JsonNode body = JSON.readTree(r.body());
        assertEquals("السلام", body.path("translation").asText());
        assertEquals("English", body.path("sourceLanguage").asText());
        assertEquals("Moroccan Arabic (Darija)", body.path("targetLanguage").asText());
        assertEquals("arabic", body.path("script").asText());
    }

    @Test
    void plainHttp11ClientsWorkToo() throws Exception {
        // The shared client above opens with the cleartext HTTP/2 upgrade; PHP, Python, curl and
        // React Native speak plain HTTP/1.1, so cover that path explicitly as well.
        HttpClient http11 = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/translate"))
                .header("Authorization", basic(USER, PASSWORD))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Goodbye\"}", StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = http11.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, r.statusCode());
        assertEquals("بسلامة", JSON.readTree(r.body()).path("translation").asText());
    }

    @Test
    void latinScriptIsHonoured() throws Exception {
        JsonNode body = JSON.readTree(postAuthed("{\"text\":\"How are you?\",\"script\":\"latin\"}").body());
        assertEquals("kif dayr?", body.path("translation").asText());
    }

    @Test
    void getVariantWorksWithQueryParameters() throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/translate?text=Thank%20you&script=latin"))
                .header("Authorization", basic(USER, PASSWORD));
        HttpResponse<String> r = send(request);
        assertEquals(200, r.statusCode());
        assertEquals("choukran", JSON.readTree(r.body()).path("translation").asText());
    }

    @Test
    void validationErrorsAreJson400And413() throws Exception {
        HttpResponse<String> blank = postAuthed("{\"text\":\"   \"}");
        assertEquals(400, blank.statusCode());
        assertEquals("invalid_request", JSON.readTree(blank.body()).path("error").asText());

        assertEquals(400, postAuthed("{}").statusCode());
        assertEquals(400, postAuthed("{\"text\":\"Hi\",\"script\":\"klingon\"}").statusCode());

        HttpResponse<String> tooLong = postAuthed("{\"text\":\"" + "x".repeat(101) + "\"}");
        assertEquals(413, tooLong.statusCode());
        assertEquals("text_too_long", JSON.readTree(tooLong.body()).path("error").asText());
    }

    @Test
    void malformedJsonIsAJson400() throws Exception {
        HttpResponse<String> r = postAuthed("{not json");
        assertEquals(400, r.statusCode());
        assertEquals("invalid_json", JSON.readTree(r.body()).path("error").asText());
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/translate"))
                .header("Authorization", basic(USER, PASSWORD))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("Hello"));
        HttpResponse<String> r = send(request);
        assertEquals(415, r.statusCode());
        assertEquals("unsupported_media_type", JSON.readTree(r.body()).path("error").asText());
    }

    @Test
    void unknownPathIsJson404() throws Exception {
        HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(base + "/nope")));
        assertEquals(404, r.statusCode());
        assertEquals("not_found", JSON.readTree(r.body()).path("error").asText());
    }

    @Test
    void llmFailuresMapToMeaningfulStatuses() throws Exception {
        assertEquals(429, postAuthed("{\"text\":\"boom-429\"}").statusCode());
        assertEquals(502, postAuthed("{\"text\":\"boom-404\"}").statusCode());
        assertEquals(504, postAuthed("{\"text\":\"boom-timeout\"}").statusCode());

        HttpResponse<String> unexpected = postAuthed("{\"text\":\"boom-unexpected\"}");
        assertEquals(500, unexpected.statusCode());
        assertTrue(!unexpected.body().contains("secret internal detail"), "internal details must not leak");
    }

    @Test
    void corsPreflightSucceedsWithoutCredentials() throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/translate"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://example.org")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type");
        HttpResponse<String> r = send(request);
        assertEquals(204, r.statusCode());
        assertEquals("*", r.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertTrue(r.headers().firstValue("Access-Control-Allow-Headers").orElse("").contains("Authorization"));
    }

    @Test
    void corsHeaderIsAddedToNormalResponses() throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/health"))
                .header("Origin", "https://example.org");
        HttpResponse<String> r = send(request);
        assertNotNull(r.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }
}
