// Run with Node 22.7+:  npm test   (the src files are ES modules without a "type" field)
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError, checkHealth, defaultServerUrl, normalizeServerUrl, translateText } from '../src/api.js';
import { base64Encode, basicAuthHeader, utf8Bytes } from '../src/base64.js';
import { pickVoice, speechLang } from '../src/voice.js';

const SETTINGS = { serverUrl: 'http://192.168.1.20:8080', username: 'student', password: 'secret' };

function fakeFetch(status, body) {
  const calls = [];
  const impl = async (url, init) => {
    calls.push({ url, init });
    return { ok: status >= 200 && status < 300, status, json: async () => body };
  };
  impl.calls = calls;
  return impl;
}

test('base64 matches Node for ASCII, accents, Arabic and emoji at every padding length', () => {
  for (const sample of ['', 'a', 'ab', 'abc', 'abcd', 'student:pässwörd', 'كيف داير؟', 'x😀y', 'a:' + 'ع'.repeat(7)]) {
    assert.equal(base64Encode(utf8Bytes(sample)), Buffer.from(sample, 'utf8').toString('base64'), sample);
  }
});

test('basicAuthHeader encodes user:password', () => {
  assert.equal(basicAuthHeader('student', 'secret'), `Basic ${Buffer.from('student:secret').toString('base64')}`);
});

test('default server URL depends on the platform', () => {
  assert.equal(defaultServerUrl('android'), 'http://10.0.2.2:8080');
  assert.equal(defaultServerUrl('ios'), 'http://localhost:8080');
});

test('normalizeServerUrl accepts the usual spellings and rejects empty input', () => {
  assert.equal(normalizeServerUrl('192.168.1.20:8080'), 'http://192.168.1.20:8080');
  assert.equal(normalizeServerUrl(' http://host:8080/ '), 'http://host:8080');
  assert.equal(normalizeServerUrl('https://example.org/api/translate'), 'https://example.org');
  assert.equal(normalizeServerUrl('https://example.org/proxy/api'), 'https://example.org/proxy');
  assert.throws(() => normalizeServerUrl('  '), ApiError);
});

test('translateText posts JSON with Basic credentials', async () => {
  const fetchImpl = fakeFetch(200, { translation: 'السلام', provider: 'mock', model: 'mock', script: 'arabic' });
  const result = await translateText(SETTINGS, { text: 'Hello', script: 'latin' }, fetchImpl);

  assert.equal(result.translation, 'السلام');
  const { url, init } = fetchImpl.calls[0];
  assert.equal(url, 'http://192.168.1.20:8080/api/translate');
  assert.equal(init.method, 'POST');
  assert.equal(init.headers.Authorization, basicAuthHeader('student', 'secret'));
  assert.deepEqual(JSON.parse(init.body), { text: 'Hello', sourceLanguage: 'auto', script: 'latin' });
  assert.ok(init.signal, 'a timeout signal is attached');
});

test('translateText maps errors to helpful kinds', async () => {
  await assert.rejects(translateText(SETTINGS, { text: 'Hi' }, fakeFetch(401, {})), (e) => e.kind === 'auth');
  await assert.rejects(
    translateText(SETTINGS, { text: 'Hi' }, fakeFetch(429, { message: 'Quota reached' })),
    (e) => e.kind === 'server' && e.message === 'Quota reached',
  );
  await assert.rejects(
    translateText(SETTINGS, { text: 'Hi' }, async () => { throw new TypeError('Network request failed'); }),
    (e) => e.kind === 'network' && e.message.includes('192.168.1.20'),
  );
  const abort = Object.assign(new Error('aborted'), { name: 'AbortError' });
  await assert.rejects(translateText(SETTINGS, { text: 'Hi' }, async () => { throw abort; }), (e) => e.kind === 'timeout');
  await assert.rejects(translateText({ ...SETTINGS, password: '' }, { text: 'Hi' }, fakeFetch(200, {})), (e) => e.kind === 'config');
  await assert.rejects(translateText(SETTINGS, { text: 'Hi' }, fakeFetch(200, { nope: 1 })), ApiError);
});

test('checkHealth accepts the service only', async () => {
  assert.equal((await checkHealth('localhost:8080', fakeFetch(200, { status: 'UP', provider: 'gemini' }))).provider, 'gemini');
  await assert.rejects(checkHealth('localhost:8080', fakeFetch(200, { hi: 1 })), ApiError);
});

test('pickVoice prefers Moroccan, then Arabic; French for Latin script', () => {
  const voices = [
    { identifier: 'a', language: 'en-US' },
    { identifier: 'b', language: 'ar-SA' },
    { identifier: 'c', language: 'ar_MA' },
    { identifier: 'd', language: 'fr-FR' },
  ];
  assert.equal(pickVoice(voices, 'arabic').identifier, 'c');
  assert.equal(pickVoice(voices.filter((v) => v.identifier !== 'c'), 'arabic').identifier, 'b');
  assert.equal(pickVoice(voices, 'latin').identifier, 'd');
  assert.equal(pickVoice([{ identifier: 'a', language: 'en-US' }], 'arabic'), null);
  assert.equal(speechLang('arabic'), 'ar-MA');
});
