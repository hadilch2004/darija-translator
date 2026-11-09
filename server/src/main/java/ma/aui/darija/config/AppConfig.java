package ma.aui.darija.config;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Immutable runtime configuration, read from environment variables.
 *
 * <p>Secrets (password, API key) never appear in {@link #toString()}.
 */
public record AppConfig(
        String host,
        int port,
        String username,
        String password,
        boolean passwordGenerated,
        String provider,
        String geminiApiKey,
        String geminiModel,
        String geminiBaseUrl,
        String ollamaUrl,
        String ollamaModel,
        int maxTextLength,
        int llmTimeoutSeconds,
        String corsAllowedOrigin) {

    public static final Set<String> PROVIDERS = Set.of("gemini", "ollama", "mock");
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._:-]{1,80}");

    public static AppConfig fromEnv() {
        return from(System::getenv);
    }

    /** Builds a configuration from any key lookup (the environment, or a map in tests). */
    public static AppConfig from(Function<String, String> env) {
        String provider = get(env, "LLM_PROVIDER", "gemini").toLowerCase();
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException(
                    "LLM_PROVIDER must be one of " + PROVIDERS + " but was '" + provider + "'");
        }

        String username = get(env, "API_USERNAME", "student");
        if (username.contains(":")) {
            throw new IllegalArgumentException("API_USERNAME must not contain ':' (HTTP Basic restriction)");
        }

        String password = get(env, "API_PASSWORD", "");
        boolean generated = password.isEmpty();
        if (generated) {
            byte[] random = new byte[18];
            new SecureRandom().nextBytes(random);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        }

        String geminiKey = get(env, "GEMINI_API_KEY", "");
        if (provider.equals("gemini") && geminiKey.isEmpty()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. Create a free key at https://aistudio.google.com/apikey "
                            + "or start with LLM_PROVIDER=ollama (local model) or LLM_PROVIDER=mock (offline demo).");
        }

        String geminiModel = stripModelsPrefix(get(env, "GEMINI_MODEL", "gemini-2.5-flash"));
        String ollamaModel = get(env, "OLLAMA_MODEL", "llama3.2");
        requireModelName("GEMINI_MODEL", geminiModel);
        requireModelName("OLLAMA_MODEL", ollamaModel);

        return new AppConfig(
                get(env, "HOST", "0.0.0.0"),
                intValue(env, "PORT", 8080, 1, 65535),
                username,
                password,
                generated,
                provider,
                geminiKey,
                geminiModel,
                stripTrailingSlash(get(env, "GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta")),
                stripTrailingSlash(get(env, "OLLAMA_URL", "http://localhost:11434")),
                ollamaModel,
                intValue(env, "MAX_TEXT_LENGTH", 5000, 1, 100_000),
                intValue(env, "LLM_TIMEOUT_SECONDS", 45, 1, 600),
                get(env, "CORS_ALLOWED_ORIGIN", "*"));
    }

    /** Name of the model actually used by the selected provider. */
    public String activeModel() {
        return switch (provider) {
            case "gemini" -> geminiModel;
            case "ollama" -> ollamaModel;
            default -> "mock";
        };
    }

    @Override
    public String toString() {
        return "AppConfig[host=" + host + ", port=" + port + ", username=" + username
                + ", provider=" + provider + ", model=" + activeModel()
                + ", maxTextLength=" + maxTextLength + ", llmTimeoutSeconds=" + llmTimeoutSeconds
                + ", corsAllowedOrigin=" + corsAllowedOrigin + ", secrets=<redacted>]";
    }

    private static String get(Function<String, String> env, String key, String fallback) {
        String value = env.apply(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intValue(Function<String, String> env, String key, int fallback, int min, int max) {
        String raw = get(env, key, Integer.toString(fallback));
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer but was '" + raw + "'");
        }
    }

    private static String stripModelsPrefix(String model) {
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void requireModelName(String key, String model) {
        if (!MODEL_NAME.matcher(model).matches()) {
            throw new IllegalArgumentException(key + " contains unsupported characters: '" + model + "'");
        }
    }
}
