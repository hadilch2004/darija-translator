package ma.aui.darija.service;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import ma.aui.darija.llm.LlmClient;
import ma.aui.darija.llm.LlmException;
import ma.aui.darija.llm.Prompts;
import ma.aui.darija.llm.Script;
import ma.aui.darija.llm.TranslationTask;
import ma.aui.darija.model.TranslationResponse;

/** Business logic: validates input, delegates to the {@link LlmClient}, cleans the answer. */
public class TranslationService {

    public static final String TARGET_LANGUAGE = "Moroccan Arabic (Darija)";

    private static final Logger LOG = Logger.getLogger(TranslationService.class.getName());
    private static final Pattern LANGUAGE_NAME = Pattern.compile("[\\p{L}][\\p{L} ()\\-]{0,39}");

    private final LlmClient llm;
    private final int maxTextLength;

    public TranslationService(LlmClient llm, int maxTextLength) {
        this.llm = llm;
        this.maxTextLength = maxTextLength;
    }

    public LlmClient llm() {
        return llm;
    }

    /**
     * Translates {@code text} into Darija.
     *
     * @param sourceLanguage optional language name; {@code null}, blank or "auto" means auto-detect
     * @param script         optional "arabic" (default) or "latin"
     * @throws ApiException  400 for invalid input, 413 when the text is too long
     * @throws LlmException  when the LLM provider fails
     */
    public TranslationResponse translate(String text, String sourceLanguage, String script) throws LlmException {
        if (text == null || text.isBlank()) {
            throw new ApiException(400, "invalid_request", "'text' is required and must not be blank.");
        }
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > maxTextLength) {
            throw new ApiException(413, "text_too_long",
                    "'text' must not exceed " + maxTextLength + " characters.");
        }

        Script outputScript;
        try {
            outputScript = Script.parse(script);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "invalid_request", e.getMessage());
        }

        String source = normaliseSource(sourceLanguage);

        long started = System.nanoTime();
        String raw = llm.translate(new TranslationTask(trimmed, source, outputScript));
        String translation = Prompts.clean(raw);
        if (translation.isEmpty()) {
            throw LlmException.empty();
        }
        // Log sizes and latency only: the user's text is private.
        LOG.info(() -> "translated %d chars via %s in %d ms".formatted(
                trimmed.length(), llm.provider(), (System.nanoTime() - started) / 1_000_000));

        return new TranslationResponse(
                translation,
                source == null ? "auto" : source,
                TARGET_LANGUAGE,
                outputScript.wireName(),
                llm.provider(),
                llm.model());
    }

    private static String normaliseSource(String sourceLanguage) {
        if (sourceLanguage == null || sourceLanguage.isBlank() || sourceLanguage.strip().equalsIgnoreCase("auto")) {
            return null;
        }
        String source = sourceLanguage.strip();
        // The value is interpolated into the prompt, so only plain language names are accepted.
        if (!LANGUAGE_NAME.matcher(source).matches()) {
            throw new ApiException(400, "invalid_request",
                    "'sourceLanguage' must be a language name such as \"English\", or \"auto\".");
        }
        return source;
    }
}
