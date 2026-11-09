import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ApiError,
  basicAuthHeader,
  checkHealth,
  normalizeServerUrl,
  originPattern,
  translateText,
} from "../lib/api.js";
import { pickVoice, speechLang } from "../lib/voice.js";

const SETTINGS = { serverUrl: "http://localhost:8080", username: "student", password: "secret" };

/** Builds a fake fetch that records the call and answers with the given status and JSON body. */
function fakeFetch(status, body) {
  const calls = [];
  const impl = async (url, init) => {
    calls.push({ url, init });
    return { ok: status >= 200 && status < 300, status, json: async () => body };
  };
  impl.calls = calls;
  return impl;
}

test("normalizeServerUrl accepts the usual spellings", () => {
  assert.equal(normalizeServerUrl("http://localhost:8080"), "http://localhost:8080");
  assert.equal(normalizeServerUrl("localhost:8080/"), "http://localhost:8080");
  assert.equal(normalizeServerUrl("  https://example.org/api/translate "), "https://example.org");
  assert.equal(normalizeServerUrl("http://10.0.2.2:8080/api"), "http://10.0.2.2:8080");
  assert.equal(normalizeServerUrl("https://example.org/proxy/api/health"), "https://example.org/proxy");
});

test("normalizeServerUrl rejects empty and nonsense input", () => {
  assert.throws(() => normalizeServerUrl(""), ApiError);
  assert.throws(() => normalizeServerUrl("http://"), ApiError);
});

test("originPattern drops the port, as Chrome match patterns require", () => {
  assert.equal(originPattern("http://192.168.1.20:8080"), "http://192.168.1.20/*");
  assert.equal(originPattern("https://api.example.org"), "https://api.example.org/*");
});

test("basicAuthHeader is RFC 7617 and UTF-8 safe", () => {
  assert.equal(basicAuthHeader("student", "secret"), `Basic ${Buffer.from("student:secret").toString("base64")}`);
  assert.equal(basicAuthHeader("student", "pässwörd"), `Basic ${Buffer.from("student:pässwörd", "utf8").toString("base64")}`);
});

test("translateText posts JSON with credentials and returns the body", async () => {
  const fetchImpl = fakeFetch(200, { translation: "السلام", provider: "mock", model: "mock", script: "arabic" });

  const result = await translateText(SETTINGS, { text: "Hello", sourceLanguage: "English", script: "arabic" }, fetchImpl);

  assert.equal(result.translation, "السلام");
  const { url, init } = fetchImpl.calls[0];
  assert.equal(url, "http://localhost:8080/api/translate");
  assert.equal(init.method, "POST");
  assert.equal(init.headers.Authorization, basicAuthHeader("student", "secret"));
  assert.deepEqual(JSON.parse(init.body), { text: "Hello", sourceLanguage: "English", script: "arabic" });
});

test("translateText maps 401 to an auth error with a helpful message", async () => {
  await assert.rejects(
    translateText(SETTINGS, { text: "Hi" }, fakeFetch(401, { error: "unauthorized" })),
    (error) => error instanceof ApiError && error.kind === "auth" && /password/i.test(error.message),
  );
});

test("translateText surfaces the server's own message for other errors", async () => {
  await assert.rejects(
    translateText(SETTINGS, { text: "Hi" }, fakeFetch(429, { error: "llm_rate_limited", message: "Quota reached" })),
    (error) => error.kind === "server" && error.message === "Quota reached" && error.status === 429,
  );
});

test("translateText reports an unreachable server", async () => {
  const failing = async () => {
    throw new TypeError("Failed to fetch");
  };
  await assert.rejects(
    translateText(SETTINGS, { text: "Hi" }, failing),
    (error) => error.kind === "network" && error.message.includes("http://localhost:8080"),
  );
});

test("translateText reports timeouts", async () => {
  const timingOut = async () => {
    throw new DOMException("timed out", "TimeoutError");
  };
  await assert.rejects(translateText(SETTINGS, { text: "Hi" }, timingOut), (error) => error.kind === "timeout");
});

test("translateText refuses to run without credentials", async () => {
  const fetchImpl = fakeFetch(200, {});
  await assert.rejects(
    translateText({ ...SETTINGS, password: "" }, { text: "Hi" }, fetchImpl),
    (error) => error.kind === "config",
  );
  assert.equal(fetchImpl.calls.length, 0);
});

test("translateText rejects a 200 without a translation", async () => {
  await assert.rejects(translateText(SETTINGS, { text: "Hi" }, fakeFetch(200, { nope: true })), ApiError);
});

test("checkHealth accepts the service and rejects impostors", async () => {
  const ok = await checkHealth("localhost:8080", fakeFetch(200, { status: "UP", provider: "gemini", model: "m" }));
  assert.equal(ok.provider, "gemini");
  await assert.rejects(checkHealth("localhost:8080", fakeFetch(200, { hello: "world" })), ApiError);
  await assert.rejects(checkHealth("localhost:8080", fakeFetch(404, null)), ApiError);
});

test("pickVoice prefers Moroccan, then any Arabic, for Arabic script", () => {
  const voices = [
    { name: "Alex", lang: "en-US" },
    { name: "Majed", lang: "ar-SA" },
    { name: "Hoda", lang: "ar_EG" },
    { name: "Fiona", lang: "ar-MA" },
  ];
  assert.equal(pickVoice(voices, "arabic").name, "Fiona");
  assert.equal(pickVoice(voices.filter((v) => v.name !== "Fiona"), "arabic").name, "Majed");
  assert.equal(pickVoice([{ name: "X", lang: "ar-LB" }], "arabic").name, "X");
  assert.equal(pickVoice([{ name: "Alex", lang: "en-US" }], "arabic"), null);
});

test("pickVoice uses a French voice for Latin-script Darija", () => {
  const voices = [{ name: "Majed", lang: "ar-SA" }, { name: "Thomas", lang: "fr-FR" }];
  assert.equal(pickVoice(voices, "latin").name, "Thomas");
  assert.equal(speechLang("latin"), "fr-FR");
  assert.equal(speechLang("arabic"), "ar-MA");
});
