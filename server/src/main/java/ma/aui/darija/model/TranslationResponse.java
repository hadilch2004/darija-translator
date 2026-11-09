package ma.aui.darija.model;

/**
 * JSON body returned by a successful translation.
 *
 * @param translation    the Darija text
 * @param sourceLanguage the language the caller declared, or "auto"
 * @param targetLanguage always "Moroccan Arabic (Darija)"
 * @param script         "arabic" or "latin"
 * @param provider       LLM provider that produced the translation (gemini, ollama, mock)
 * @param model          model name used by the provider
 */
public record TranslationResponse(
        String translation,
        String sourceLanguage,
        String targetLanguage,
        String script,
        String provider,
        String model) {
}
