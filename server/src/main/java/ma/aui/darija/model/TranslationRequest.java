package ma.aui.darija.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON body of {@code POST /api/translate}.
 *
 * @param text           the text to translate (required)
 * @param sourceLanguage optional source language name, e.g. "English"; omit or "auto" to auto-detect
 * @param script         optional output script: "arabic" (default) or "latin" (Arabizi-style)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TranslationRequest(String text, String sourceLanguage, String script) {
}
