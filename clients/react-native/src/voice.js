// Voice selection for expo-speech. Pure, so it is testable in Node.

/**
 * Darija in Arabic letters is read best by an Arabic voice (Moroccan if the phone has one);
 * Latinised Darija ("Arabizi") sounds closest to French.
 */
const PREFERENCES = {
  arabic: ['ar-MA', 'ar-DZ', 'ar-TN', 'ar-SA', 'ar-EG', 'ar'],
  latin: ['fr-FR', 'fr'],
};

const normalise = (lang) => String(lang ?? '').replace('_', '-').toLowerCase();

/** Language tag to use when the phone has no matching voice installed. */
export function speechLang(script) {
  return script === 'latin' ? 'fr-FR' : 'ar-MA';
}

/** Best installed voice ({ identifier, language, ... } from Speech.getAvailableVoicesAsync) or null. */
export function pickVoice(voices, script) {
  const preferences = PREFERENCES[script] ?? PREFERENCES.arabic;
  for (const wanted of preferences) {
    const target = wanted.toLowerCase();
    const match = voices.find((voice) => {
      const lang = normalise(voice.language);
      return lang === target || (!target.includes('-') && lang.startsWith(`${target}-`));
    });
    if (match) return match;
  }
  return null;
}
