import {
  ApiError,
  DEFAULT_SETTINGS,
  MAX_TEXT_LENGTH,
  checkHealth,
  normalizeServerUrl,
  originPattern,
  translateText,
} from "../lib/api.js";
import { pickVoice, speechLang } from "../lib/voice.js";

const $ = (id) => document.getElementById(id);
const el = {
  status: $("status"),
  settingsToggle: $("settingsToggle"),
  settings: $("settings"),
  serverUrl: $("serverUrl"),
  username: $("username"),
  password: $("password"),
  sourceLanguage: $("sourceLanguage"),
  script: $("script"),
  recognitionLang: $("recognitionLang"),
  autoSpeak: $("autoSpeak"),
  autoCopy: $("autoCopy"),
  input: $("input"),
  counter: $("counter"),
  mic: $("mic"),
  clear: $("clear"),
  translate: $("translate"),
  error: $("error"),
  errorText: $("errorText"),
  micHelp: $("micHelp"),
  result: $("result"),
  output: $("output"),
  speak: $("speak"),
  copy: $("copy"),
  meta: $("meta"),
};

let settings = { ...DEFAULT_SETTINGS };
let busy = false;
let recognition = null;
let lastNonce = 0;
let lastRequest = { text: "", at: 0 };
let lastResult = null;

// ───────────────────────────── settings ─────────────────────────────

async function loadSettings() {
  const stored = await chrome.storage.local.get(Object.keys(DEFAULT_SETTINGS));
  settings = { ...DEFAULT_SETTINGS, ...stored };
  el.serverUrl.value = settings.serverUrl;
  el.username.value = settings.username;
  el.password.value = settings.password;
  el.sourceLanguage.value = settings.sourceLanguage;
  el.script.value = settings.script;
  el.recognitionLang.value = settings.recognitionLang;
  el.autoSpeak.checked = settings.autoSpeak;
  el.autoCopy.checked = settings.autoCopy;
}

async function saveSettings(event) {
  event.preventDefault();
  let serverUrl;
  try {
    serverUrl = normalizeServerUrl(el.serverUrl.value);
  } catch (error) {
    showError(error);
    return;
  }

  // Non-localhost servers need host access; this must run inside the click's user gesture.
  const origins = [originPattern(serverUrl)];
  try {
    if (!(await chrome.permissions.contains({ origins }))) {
      const granted = await chrome.permissions.request({ origins });
      if (!granted) {
        showError(new ApiError("config", "Access to that server was not allowed, so requests would be blocked."));
        return;
      }
    }
  } catch (error) {
    showError(new ApiError("config", `Could not request access to the server: ${error.message}`));
    return;
  }

  settings = {
    serverUrl,
    username: el.username.value.trim(),
    password: el.password.value,
    sourceLanguage: el.sourceLanguage.value,
    script: el.script.value,
    recognitionLang: el.recognitionLang.value,
    autoSpeak: el.autoSpeak.checked,
    autoCopy: el.autoCopy.checked,
  };
  await chrome.storage.local.set(settings);
  el.serverUrl.value = serverUrl;
  hideError();
  await refreshStatus();
  if (el.status.classList.contains("ok")) toggleSettings(false);
}

function toggleSettings(open = el.settings.hidden) {
  el.settings.hidden = !open;
  el.settingsToggle.setAttribute("aria-expanded", String(open));
}

async function refreshStatus() {
  el.status.className = "status";
  el.status.textContent = "Checking server…";
  try {
    const health = await checkHealth(settings.serverUrl);
    el.status.className = "status ok";
    el.status.textContent = `Online · ${health.provider}`;
    el.status.title = `Model: ${health.model}`;
  } catch (error) {
    el.status.className = "status bad";
    el.status.textContent = "Server offline";
    el.status.title = error.message;
  }
}

// ───────────────────────────── translating ─────────────────────────────

function updateCounter() {
  el.counter.textContent = `${el.input.value.length} / ${MAX_TEXT_LENGTH}`;
}

function setBusy(value) {
  busy = value;
  el.translate.disabled = value;
  el.translate.textContent = value ? "Translating…" : "Translate";
}

function showError(error, { mic = false } = {}) {
  el.errorText.textContent = error instanceof Error ? error.message : String(error);
  el.micHelp.hidden = !mic;
  el.error.hidden = false;
  if (error instanceof ApiError && (error.kind === "config" || error.kind === "auth")) toggleSettings(true);
}

function hideError() {
  el.error.hidden = true;
  el.micHelp.hidden = true;
}

/**
 * Translates `text` (default: what is in the box). Identical requests within a few seconds are
 * ignored: the context menu and the "grab selection on open" path can both deliver the same text.
 */
async function requestTranslation(text = el.input.value, { speak = false } = {}) {
  const trimmed = text.trim();
  if (!trimmed || busy) return;
  if (trimmed === lastRequest.text && Date.now() - lastRequest.at < 4000) return;
  lastRequest = { text: trimmed, at: Date.now() };

  el.input.value = trimmed;
  updateCounter();
  setBusy(true);
  hideError();
  try {
    const result = await translateText(settings, {
      text: trimmed,
      sourceLanguage: settings.sourceLanguage,
      script: settings.script,
    });
    showResult(result);
    if (settings.autoCopy) await copyResult();
    if (speak || settings.autoSpeak) speakResult();
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
}

function showResult(result) {
  lastResult = result;
  const arabic = result.script !== "latin";
  el.output.textContent = result.translation;
  el.output.dir = arabic ? "rtl" : "ltr";
  el.output.lang = arabic ? "ary-Arab" : "ary-Latn";
  el.meta.textContent = `${result.provider} · ${result.model}`;
  el.result.hidden = false;
}

async function copyResult() {
  if (!lastResult) return;
  try {
    await navigator.clipboard.writeText(lastResult.translation);
    el.copy.textContent = "✔ Copied";
    setTimeout(() => (el.copy.textContent = "📋 Copy"), 1500);
  } catch {
    showError(new Error("Could not copy to the clipboard."));
  }
}

// ───────────────────────────── read aloud ─────────────────────────────

function speakResult() {
  if (!lastResult || !("speechSynthesis" in window)) return;
  const synth = window.speechSynthesis;
  synth.cancel();
  const utterance = new SpeechSynthesisUtterance(lastResult.translation);
  const voice = pickVoice(synth.getVoices(), lastResult.script);
  if (voice) {
    utterance.voice = voice;
    utterance.lang = voice.lang;
  } else {
    utterance.lang = speechLang(lastResult.script);
  }
  utterance.rate = 0.95;
  utterance.onstart = () => (el.speak.textContent = "⏹ Stop");
  utterance.onend = utterance.onerror = () => (el.speak.textContent = "🔊 Read aloud");
  synth.speak(utterance);
}

function toggleSpeech() {
  if (window.speechSynthesis?.speaking) {
    window.speechSynthesis.cancel();
    el.speak.textContent = "🔊 Read aloud";
  } else {
    speakResult();
  }
}

// ───────────────────────────── voice input ─────────────────────────────

function toggleListening() {
  if (recognition) {
    recognition.stop();
    return;
  }
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    showError(new Error("Speech recognition is not available in this browser."));
    return;
  }

  window.speechSynthesis?.cancel();
  hideError();
  const session = new Recognition();
  session.lang = settings.recognitionLang;
  session.interimResults = true;
  session.continuous = false;
  let finalText = "";

  session.onresult = (event) => {
    let interim = "";
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const piece = event.results[i][0].transcript;
      if (event.results[i].isFinal) finalText += piece;
      else interim += piece;
    }
    el.input.value = `${finalText}${interim}`.trim();
    updateCounter();
  };
  session.onerror = (event) => {
    if (event.error === "not-allowed" || event.error === "service-not-allowed") {
      showError(new Error("Microphone access is blocked. Allow it once, then press Speak again."), { mic: true });
    } else if (event.error !== "no-speech" && event.error !== "aborted") {
      showError(new Error(`Speech recognition failed (${event.error}).`));
    }
  };
  session.onend = () => {
    recognition = null;
    el.mic.classList.remove("listening");
    el.mic.textContent = "🎤 Speak";
    // Voice to voice: whatever was said is translated and read back in Darija.
    if (finalText.trim()) requestTranslation(finalText, { speak: true });
  };

  recognition = session;
  el.mic.classList.add("listening");
  el.mic.textContent = "⏹ Listening…";
  try {
    session.start();
  } catch (error) {
    recognition = null;
    showError(error);
  }
}

// ───────────────────────────── text hand-over ─────────────────────────────

/** Picks up text the background worker left for us (context menu / shortcut). */
async function consumePending() {
  const { pending } = await chrome.storage.session.get("pending");
  if (!pending || pending.nonce === lastNonce) return;
  lastNonce = pending.nonce;
  await chrome.storage.session.remove("pending");
  requestTranslation(pending.text);
}

/** On open, copy whatever is selected on the page into the box and translate it right away. */
async function grabSelection() {
  try {
    const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    if (!tab?.id) return;
    const [frame] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: () => String(window.getSelection() ?? ""),
    });
    const selected = frame?.result?.trim();
    if (selected && !el.input.value.trim()) requestTranslation(selected);
  } catch {
    // No access to this tab (chrome:// page, or the panel was opened without a user gesture).
  }
}

// ───────────────────────────── start-up ─────────────────────────────

el.settingsToggle.addEventListener("click", () => toggleSettings());
el.settings.addEventListener("submit", saveSettings);
el.input.addEventListener("input", updateCounter);
el.input.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) requestTranslation();
});
el.translate.addEventListener("click", () => requestTranslation());
el.clear.addEventListener("click", () => {
  el.input.value = "";
  el.result.hidden = true;
  lastRequest = { text: "", at: 0 };
  hideError();
  updateCounter();
  el.input.focus();
});
el.mic.addEventListener("click", toggleListening);
el.micHelp.addEventListener("click", () => chrome.tabs.create({ url: chrome.runtime.getURL("permissions.html") }));
el.speak.addEventListener("click", toggleSpeech);
el.copy.addEventListener("click", copyResult);
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "session" && changes.pending?.newValue) consumePending();
});
// Chrome loads the voice list lazily; touching it early makes it ready by the time we speak.
window.speechSynthesis?.getVoices();

(async function init() {
  await loadSettings();
  updateCounter();
  if (!settings.password) toggleSettings(true);
  refreshStatus();
  await consumePending();
  if (!el.input.value.trim()) await grabSelection();
})();
