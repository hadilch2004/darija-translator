package ma.aui.darija.llm;

/** Strategy interface implemented by every translation back end (Gemini, Ollama, mock). */
public interface LlmClient {

    /** Translates the task's text into Moroccan Arabic and returns the raw model output. */
    String translate(TranslationTask task) throws LlmException;

    /** Short provider id: "gemini", "ollama" or "mock". */
    String provider();

    /** Model name reported to API consumers. */
    String model();
}
