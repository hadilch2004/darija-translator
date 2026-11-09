package ma.aui.darija;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import ma.aui.darija.config.AppConfig;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    private static AppConfig config(Map<String, String> env) {
        return AppConfig.from(env::get);
    }

    @Test
    void geminiRequiresAnApiKey() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> config(Map.of()));
        assertTrue(e.getMessage().contains("GEMINI_API_KEY"));
    }

    @Test
    void mockProviderNeedsNoKeyAndGetsDefaults() {
        AppConfig c = config(Map.of("LLM_PROVIDER", "mock"));
        assertEquals(8080, c.port());
        assertEquals("student", c.username());
        assertEquals("mock", c.activeModel());
        assertTrue(c.passwordGenerated());
        assertFalse(c.password().isBlank());
    }

    @Test
    void explicitPasswordIsKeptAndNeverPrinted() {
        AppConfig c = config(Map.of(
                "LLM_PROVIDER", "gemini", "GEMINI_API_KEY", "super-secret-key", "API_PASSWORD", "hunter2hunter2"));
        assertFalse(c.passwordGenerated());
        assertEquals("hunter2hunter2", c.password());
        String text = c.toString();
        assertFalse(text.contains("super-secret-key"));
        assertFalse(text.contains("hunter2hunter2"));
    }

    @Test
    void geminiModelPrefixIsNormalised() {
        AppConfig c = config(Map.of("GEMINI_API_KEY", "k", "GEMINI_MODEL", "models/gemini-x"));
        assertEquals("gemini-x", c.geminiModel());
    }

    @Test
    void rejectsBadValues() {
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("LLM_PROVIDER", "openai")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("LLM_PROVIDER", "mock", "PORT", "abc")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("LLM_PROVIDER", "mock", "PORT", "70000")));
        assertThrows(IllegalArgumentException.class, () -> config(Map.of("LLM_PROVIDER", "mock", "API_USERNAME", "a:b")));
        assertThrows(IllegalArgumentException.class,
                () -> config(Map.of("GEMINI_API_KEY", "k", "GEMINI_MODEL", "x/../../etc")));
    }
}
