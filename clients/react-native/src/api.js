// Pure client logic for the REST service (no React, no native modules), so it is testable in Node.
import { basicAuthHeader } from './base64.js';

export const MAX_TEXT_LENGTH = 5000;
const REQUEST_TIMEOUT_MS = 60000;

/** An error whose message is safe and useful to show to the user. */
export class ApiError extends Error {
  /** @param {'config'|'network'|'timeout'|'auth'|'server'} kind */
  constructor(kind, message, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
  }
}

/**
 * Android emulators reach the host machine at 10.0.2.2; iOS simulators share its localhost.
 * A real phone needs the computer's LAN address instead (e.g. http://192.168.1.20:8080).
 */
export function defaultServerUrl(platformOs) {
  return platformOs === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
}

export function defaultSettings(platformOs) {
  return {
    serverUrl: defaultServerUrl(platformOs),
    username: 'student',
    password: '',
    script: 'arabic',
    sourceLanguage: 'auto',
  };
}

/** Accepts "192.168.1.20:8080", "http://host:8080/api/translate", ... and returns "http://host:8080". */
export function normalizeServerUrl(input) {
  let value = String(input ?? '').trim();
  if (!value) throw new ApiError('config', 'Enter the server URL, e.g. http://192.168.1.20:8080');
  if (!/^https?:\/\//i.test(value)) value = `http://${value}`;
  const match = value.match(/^(https?:\/\/[^/?#\s]+)([^?#]*)/i);
  if (!match) throw new ApiError('config', 'The server URL is not valid. Example: http://192.168.1.20:8080');
  const path = match[2].replace(/\/+$/, '').replace(/\/api(\/translate|\/health)?$/i, '');
  return match[1] + path;
}

function describeFetchFailure(error, base) {
  if (error?.name === 'AbortError') {
    return new ApiError('timeout', 'The server took too long to answer. Try again.');
  }
  return new ApiError('network', `Cannot reach the server at ${base}. Is it running, and is this phone on the same network?`);
}

function fetchWithTimeout(fetchImpl, url, init, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  return fetchImpl(url, { ...init, signal: controller.signal }).finally(() => clearTimeout(timer));
}

async function readJson(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

/**
 * Calls POST /api/translate.
 * @returns {Promise<{translation: string, provider: string, model: string, script: string}>}
 */
export async function translateText(settings, { text, sourceLanguage = 'auto', script = 'arabic' }, fetchImpl = fetch) {
  const base = normalizeServerUrl(settings.serverUrl);
  if (!settings.username || !settings.password) {
    throw new ApiError('config', 'Enter your username and password in Settings.');
  }

  let response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      `${base}/api/translate`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: basicAuthHeader(settings.username, settings.password),
        },
        body: JSON.stringify({ text, sourceLanguage, script }),
      },
      REQUEST_TIMEOUT_MS,
    );
  } catch (error) {
    throw describeFetchFailure(error, base);
  }

  const body = await readJson(response);
  if (response.ok) {
    if (typeof body?.translation !== 'string' || !body.translation) {
      throw new ApiError('server', 'The server answered, but without a translation.', response.status);
    }
    return body;
  }
  if (response.status === 401) {
    throw new ApiError('auth', 'Wrong username or password. Check Settings.', 401);
  }
  throw new ApiError('server', body?.message ?? `The server answered with HTTP ${response.status}.`, response.status);
}

/** Calls the unauthenticated GET /api/health. */
export async function checkHealth(serverUrl, fetchImpl = fetch) {
  const base = normalizeServerUrl(serverUrl);
  let response;
  try {
    response = await fetchWithTimeout(fetchImpl, `${base}/api/health`, {}, 5000);
  } catch (error) {
    throw describeFetchFailure(error, base);
  }
  const body = await readJson(response);
  if (!response.ok || body?.status !== 'UP') {
    throw new ApiError('server', `${base} does not look like the Darija Translator service.`, response.status);
  }
  return body;
}
