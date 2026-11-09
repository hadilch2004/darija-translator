package ma.aui.darija.llm;

import java.util.Locale;
import java.util.Map;

/**
 * Offline stand-in used for demos and automated tests. Knows a handful of phrases and marks every
 * other answer with "[MOCK]" so nobody mistakes it for a real translation.
 */
public final class MockLlmClient implements LlmClient {

    /** lower-case English phrase -> {Arabic script, Latin script}. */
    private static final Map<String, String[]> PHRASES = Map.of(
            "hello", new String[] {"السلام", "salam"},
            "how are you", new String[] {"كيف داير؟", "kif dayr?"},
            "thank you", new String[] {"شكرا", "choukran"},
            "good morning", new String[] {"صباح الخير", "sbah lkhir"},
            "goodbye", new String[] {"بسلامة", "bslama"},
            "where is the train station", new String[] {"فين كاينة محطة القطار؟", "fin kayna mhattat l9tar?"});

    @Override
    public String translate(TranslationTask task) {
        String key = task.text().toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+$", "").strip();
        String[] known = PHRASES.get(key);
        if (known != null) {
            return task.script() == Script.LATIN ? known[1] : known[0];
        }
        return "[MOCK] " + task.text();
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock";
    }
}
