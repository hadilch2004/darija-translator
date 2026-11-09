// Pure client logic for the REST service. No DOM or chrome.* access, so it is unit-testable in Node.

export const DEFAULT_SETTINGS = Object.freeze({
  serverUrl: "http://localhost:8080",
  username: "student",
  password: "",
  script: "arabic",
  sourceLanguage: "auto",
  recognitionLang: "en-US",
  autoSpeak: false,
  autoCopy: false,
});

export const MAX_TEXT_LENGTH = 5000;
const REQUEST_TIMEOUT_MS = 60_000;

/** An error whose message is safe and useful to show to the user. */
export class ApiError extends Error {
  /** @param {"config"|"network"|"timeout"|"auth"|"server"} kind */
  constructor(kind, message, status = 0) {
    super(message);
    this.name = "ApiError";
    this.kind = kind;
    this.status = status;
  }
}

/** Accepts "localhost:8080", "http://host:8080/api/translate", ... and returns "http://host:8080". */
export function normalizeServerUrl(input) {
  let value = String(input ?? "").trim();
  if (!value) {
    throw new ApiError("config", "Enter the server URL in ⚙ Settings, e.g. http://localhost:8080");
  }
  if (!/^https?:\/\//i.test(value)) value = `http://${value}`;
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new ApiError("config", "The server URL is not valid. Example: http://localhost:8080");
  }
  const path = url.pathname.replace(/\/+$/, "").replace(/\/api(\/translate|\/health)?$/i, "");
  return url.origin + path;
}

/** Chrome match pattern (ports are not part of patterns) used to request host access. */
export function originPattern(serverUrl) {
  const url = new URL(normalizeServerUrl(serverUrl));
  return `${url.protocol}//${url.hostname}/*`;
}

/** `Authorization` header value for HTTP Basic, safe for non-ASCII credentials. */
export function basicAuthHeader(username, password) {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return `Basic ${btoa(binary)}`;
}

async function readJson(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function describeFetchFailure(error, base) {
  if (error?.name === "TimeoutError" || error?.name === "AbortError") {
    return new ApiError("timeout", "The server took too long to answer. Try again.");
  }
  return new ApiError("network", `Cannot reach the server at ${base}. Is it running?`);
}

/**
 * Calls POST /api/translate.
 * @returns {Promise<{translation: string, provider: string, model: string, script: string}>}
 */
export async function translateText(settings, { text, sourceLanguage = "auto", script = "arabic" }, fetchImpl = fetch) {
  const base = normalizeServerUrl(settings.serverUrl);
  if (!settings.username || !settings.password) {
    throw new ApiError("config", "Enter your username and password in ⚙ Settings.");
  }

  let response;
  try {
    response = await fetchImpl(`${base}/api/translate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: basicAuthHeader(settings.username, settings.password),
      },
      body: JSON.stringify({ text, sourceLanguage, script }),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    throw describeFetchFailure(error, base);
  }

  const body = await readJson(response);
  if (response.ok) {
    if (typeof body?.translation !== "string" || !body.translation) {
      throw new ApiError("server", "The server answered, but without a translation.", response.status);
    }
    return body;
  }
  if (response.status === 401) {
    throw new ApiError("auth", "Wrong username or password. Check ⚙ Settings.", 401);
  }
  throw new ApiError("server", body?.message ?? `The server answered with HTTP ${response.status}.`, response.status);
}

/** Calls the unauthenticated GET /api/health. */
export async function checkHealth(serverUrl, fetchImpl = fetch) {
  const base = normalizeServerUrl(serverUrl);
  let response;
  try {
    response = await fetchImpl(`${base}/api/health`, { signal: AbortSignal.timeout(5000) });
  } catch (error) {
    throw describeFetchFailure(error, base);
  }
  const body = await readJson(response);
  if (!response.ok || body?.status !== "UP") {
    throw new ApiError("server", `${base} does not look like the Darija Translator service.`, response.status);
  }
  return body;
}
