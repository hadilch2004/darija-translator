package ma.aui.darija.llm;

/**
 * A validated unit of work handed to an {@link LlmClient}.
 *
 * @param text           the text to translate, already trimmed and length-checked
 * @param sourceLanguage source language name, or {@code null} to let the model detect it
 * @param script         desired output script
 */
public record TranslationTask(String text, String sourceLanguage, Script script) {
}
