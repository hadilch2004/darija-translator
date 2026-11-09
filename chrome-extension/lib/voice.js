// Text-to-speech helpers. Pure functions, so they can be tested without a browser.

/**
 * Language codes tried in order. Darija written in Arabic letters is best read by an Arabic voice
 * (Moroccan if the OS has one). Latinised Darija ("Arabizi") is closest to French pronunciation.
 */
const PREFERENCES = {
  arabic: ["ar-MA", "ar-DZ", "ar-TN", "ar-SA", "ar-EG", "ar"],
  latin: ["fr-FR", "fr"],
};

function normalise(lang) {
  return String(lang ?? "").replace("_", "-").toLowerCase();
}

/** BCP-47 tag to put on the utterance when no matching voice is installed. */
export function speechLang(script) {
  return script === "latin" ? "fr-FR" : "ar-MA";
}

/** Picks the best installed voice for the script, or `null` to let the browser decide. */
export function pickVoice(voices, script) {
  const preferences = PREFERENCES[script] ?? PREFERENCES.arabic;
  for (const wanted of preferences) {
    const target = wanted.toLowerCase();
    const match = voices.find((voice) => {
      const lang = normalise(voice.lang);
      return lang === target || (!target.includes("-") && lang.startsWith(`${target}-`));
    });
    if (match) return match;
  }
  return null;
}
