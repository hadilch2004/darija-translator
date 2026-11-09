package ma.aui.darija.llm;

import java.util.Locale;

/** Writing system used for the Darija output. */
public enum Script {
    /** Standard Arabic letters, e.g. "كيف داير؟". */
    ARABIC,
    /** Latinised Darija ("Arabizi"), e.g. "kif dayr?". */
    LATIN;

    /** Wire value used in the JSON API ("arabic" / "latin"). */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses the API value; {@code null} or blank means {@link #ARABIC}. */
    public static Script parse(String value) {
        if (value == null || value.isBlank()) {
            return ARABIC;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "arabic" -> ARABIC;
            case "latin" -> LATIN;
            default -> throw new IllegalArgumentException("script must be 'arabic' or 'latin'");
        };
    }
}
