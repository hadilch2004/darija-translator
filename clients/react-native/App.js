import { StatusBar } from 'expo-status-bar';
import * as Clipboard from 'expo-clipboard';
import * as SecureStore from 'expo-secure-store';
import * as Speech from 'expo-speech';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useColorScheme,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import { ApiError, MAX_TEXT_LENGTH, checkHealth, defaultSettings, normalizeServerUrl, translateText } from './src/api';
import { pickVoice, speechLang } from './src/voice';

const SETTINGS_KEY = 'darija-settings';
const SOURCES = ['auto', 'English', 'French', 'Spanish', 'German'];

const palettes = {
  light: { bg: '#ffffff', fg: '#1c1f23', muted: '#5f6872', card: '#f4f6f8', border: '#d5dae0', accent: '#006233', onAccent: '#ffffff', dangerBg: '#fdecec', dangerFg: '#8a1f1f' },
  dark: { bg: '#14171a', fg: '#e8eaed', muted: '#9aa3ad', card: '#1f2428', border: '#3a4147', accent: '#2fa86a', onAccent: '#07130d', dangerBg: '#3a1c1c', dangerFg: '#ffb4b4' },
};

export default function App() {
  return (
    <SafeAreaProvider>
      <Translator />
    </SafeAreaProvider>
  );
}

function Translator() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = palettes[scheme];
  const styles = useMemo(() => makeStyles(colors), [colors]);

  const [settings, setSettings] = useState(() => defaultSettings(Platform.OS));
  const [draft, setDraft] = useState(settings);
  const [showSettings, setShowSettings] = useState(false);
  const [serverStatus, setServerStatus] = useState({ state: 'checking', text: 'Checking server…' });

  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [speaking, setSpeaking] = useState(false);
  const [copied, setCopied] = useState(false);

  const refreshStatus = useCallback(async (serverUrl) => {
    setServerStatus({ state: 'checking', text: 'Checking server…' });
    try {
      const health = await checkHealth(serverUrl);
      setServerStatus({ state: 'ok', text: `Online · ${health.provider}` });
    } catch (e) {
      setServerStatus({ state: 'bad', text: 'Server offline' });
    }
  }, []);

  // Load saved settings once.
  useEffect(() => {
    (async () => {
      let loaded = defaultSettings(Platform.OS);
      try {
        const stored = await SecureStore.getItemAsync(SETTINGS_KEY);
        if (stored) loaded = { ...loaded, ...JSON.parse(stored) };
      } catch {
        // First run or unreadable store: keep the defaults.
      }
      setSettings(loaded);
      setDraft(loaded);
      if (!loaded.password) setShowSettings(true);
      refreshStatus(loaded.serverUrl);
    })();
  }, [refreshStatus]);

  const saveSettings = async () => {
    try {
      const next = { ...draft, serverUrl: normalizeServerUrl(draft.serverUrl), username: draft.username.trim() };
      await SecureStore.setItemAsync(SETTINGS_KEY, JSON.stringify(next));
      setSettings(next);
      setDraft(next);
      setError(null);
      await refreshStatus(next.serverUrl);
      setShowSettings(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const translate = async () => {
    const trimmed = text.trim();
    if (!trimmed || busy) return;
    Speech.stop();
    setSpeaking(false);
    setBusy(true);
    setError(null);
    try {
      const answer = await translateText(settings, {
        text: trimmed,
        sourceLanguage: settings.sourceLanguage,
        script: settings.script,
      });
      setResult(answer);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      if (e instanceof ApiError && (e.kind === 'config' || e.kind === 'auth')) setShowSettings(true);
    } finally {
      setBusy(false);
    }
  };

  const toggleSpeech = async () => {
    if (!result) return;
    if (await Speech.isSpeakingAsync()) {
      Speech.stop();
      setSpeaking(false);
      return;
    }
    const voices = await Speech.getAvailableVoicesAsync().catch(() => []);
    const voice = pickVoice(voices, result.script);
    Speech.speak(result.translation, {
      language: voice?.language ?? speechLang(result.script),
      voice: voice?.identifier,
      rate: 0.95,
      onStart: () => setSpeaking(true),
      onDone: () => setSpeaking(false),
      onStopped: () => setSpeaking(false),
      onError: () => setSpeaking(false),
    });
  };

  const copyResult = async () => {
    if (!result) return;
    await Clipboard.setStringAsync(result.translation);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const clearAll = () => {
    Speech.stop();
    setText('');
    setResult(null);
    setError(null);
  };

  const arabic = result?.script !== 'latin';

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Text style={styles.title} accessibilityRole="header">Darija Translator</Text>
            <Text style={[styles.status, serverStatus.state === 'ok' && styles.statusOk, serverStatus.state === 'bad' && styles.statusBad]}>
              ● {serverStatus.text}
            </Text>
            <Pressable accessibilityLabel="Settings" onPress={() => setShowSettings((v) => !v)} style={styles.iconButton}>
              <Text style={styles.iconText}>⚙</Text>
            </Pressable>
          </View>

          {showSettings && (
            <View style={styles.card}>
              <Field styles={styles} label="Server URL" value={draft.serverUrl} autoCapitalize="none" keyboardType="url"
                onChangeText={(v) => setDraft({ ...draft, serverUrl: v })} />
              <Field styles={styles} label="Username" value={draft.username} autoCapitalize="none"
                onChangeText={(v) => setDraft({ ...draft, username: v })} />
              <Field styles={styles} label="Password" value={draft.password} secureTextEntry autoCapitalize="none"
                onChangeText={(v) => setDraft({ ...draft, password: v })} />

              <Text style={styles.label}>Write Darija in</Text>
              <View style={styles.segment}>
                {[['arabic', 'Arabic letters'], ['latin', 'Latin letters']].map(([value, label]) => (
                  <Pressable key={value} onPress={() => setDraft({ ...draft, script: value })}
                    style={[styles.segmentItem, draft.script === value && styles.segmentActive]}>
                    <Text style={draft.script === value ? styles.segmentActiveText : styles.segmentText}>{label}</Text>
                  </Pressable>
                ))}
              </View>

              <Text style={styles.label}>Translate from</Text>
              <View style={styles.segmentWrap}>
                {SOURCES.map((value) => (
                  <Pressable key={value} onPress={() => setDraft({ ...draft, sourceLanguage: value })}
                    style={[styles.chip, draft.sourceLanguage === value && styles.segmentActive]}>
                    <Text style={draft.sourceLanguage === value ? styles.segmentActiveText : styles.segmentText}>
                      {value === 'auto' ? 'Detect' : value}
                    </Text>
                  </Pressable>
                ))}
              </View>

              <Button styles={styles} primary label="Save & test" onPress={saveSettings} />
              <Text style={styles.hint}>The password is kept in the device's secure storage (Keychain / Keystore).</Text>
            </View>
          )}

          <TextInput
            style={styles.input}
            multiline
            value={text}
            onChangeText={setText}
            maxLength={MAX_TEXT_LENGTH}
            placeholder="Type or paste text to translate…"
            placeholderTextColor={colors.muted}
            accessibilityLabel="Text to translate"
          />
          <View style={styles.row}>
            <Text style={styles.muted}>{text.length} / {MAX_TEXT_LENGTH}</Text>
            <View style={styles.buttons}>
              <Button styles={styles} label="Clear" onPress={clearAll} />
              <Button styles={styles} primary label={busy ? 'Translating…' : 'Translate'} onPress={translate} disabled={busy || !text.trim()} />
            </View>
          </View>
          {busy && <ActivityIndicator style={styles.spinner} color={colors.accent} />}

          {error && (
            <View style={styles.error} accessibilityRole="alert">
              <Text style={{ color: colors.dangerFg }}>{error}</Text>
            </View>
          )}

          {result && (
            <View style={styles.card} accessibilityLiveRegion="polite">
              <Text style={styles.cardTitle}>DARIJA</Text>
              <Text selectable style={[styles.output, arabic ? styles.rtl : styles.ltr]}>{result.translation}</Text>
              <View style={styles.row}>
                <Button styles={styles} label={speaking ? '⏹ Stop' : '🔊 Read aloud'} onPress={toggleSpeech} />
                <Button styles={styles} label={copied ? '✔ Copied' : '📋 Copy'} onPress={copyResult} />
              </View>
              <Text style={styles.muted}>{result.provider} · {result.model}</Text>
            </View>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function Field({ styles, label, ...inputProps }) {
  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <TextInput style={styles.field} autoCorrect={false} {...inputProps} />
    </View>
  );
}

function Button({ styles, label, primary = false, disabled = false, onPress }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [styles.button, primary && styles.buttonPrimary, (disabled || pressed) && styles.buttonDim]}
    >
      <Text style={primary ? styles.buttonPrimaryText : styles.buttonText}>{label}</Text>
    </Pressable>
  );
}

const makeStyles = (c) =>
  StyleSheet.create({
    flex: { flex: 1 },
    screen: { flex: 1, backgroundColor: c.bg },
    content: { padding: 16, gap: 12 },
    header: { flexDirection: 'row', alignItems: 'center', gap: 8 },
    title: { flex: 1, fontSize: 20, fontWeight: '700', color: c.fg },
    status: { fontSize: 12, color: c.muted },
    statusOk: { color: '#1a7f37' },
    statusBad: { color: '#c1272d' },
    iconButton: { padding: 6 },
    iconText: { fontSize: 20, color: c.fg },
    card: { backgroundColor: c.card, borderColor: c.border, borderWidth: 1, borderRadius: 10, padding: 12, gap: 8 },
    cardTitle: { fontSize: 12, letterSpacing: 1, color: c.muted },
    label: { fontSize: 12, color: c.muted, marginTop: 4 },
    field: { borderWidth: 1, borderColor: c.border, borderRadius: 8, padding: 10, color: c.fg, backgroundColor: c.bg, marginTop: 2 },
    input: { minHeight: 120, borderWidth: 1, borderColor: c.border, borderRadius: 10, padding: 12, fontSize: 16, color: c.fg, backgroundColor: c.bg, textAlignVertical: 'top' },
    row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
    buttons: { flexDirection: 'row', gap: 8 },
    muted: { fontSize: 12, color: c.muted },
    hint: { fontSize: 11, color: c.muted },
    spinner: { marginTop: 4 },
    button: { paddingVertical: 9, paddingHorizontal: 14, borderRadius: 8, borderWidth: 1, borderColor: c.border, backgroundColor: c.card, alignItems: 'center' },
    buttonPrimary: { backgroundColor: c.accent, borderColor: c.accent },
    buttonDim: { opacity: 0.55 },
    buttonText: { color: c.fg, fontSize: 15 },
    buttonPrimaryText: { color: c.onAccent, fontSize: 15, fontWeight: '600' },
    segment: { flexDirection: 'row', gap: 8 },
    segmentWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    segmentItem: { flex: 1, paddingVertical: 8, borderRadius: 8, borderWidth: 1, borderColor: c.border, alignItems: 'center' },
    chip: { paddingVertical: 6, paddingHorizontal: 12, borderRadius: 16, borderWidth: 1, borderColor: c.border },
    segmentActive: { backgroundColor: c.accent, borderColor: c.accent },
    segmentText: { color: c.fg },
    segmentActiveText: { color: c.onAccent, fontWeight: '600' },
    error: { backgroundColor: c.dangerBg, borderRadius: 8, padding: 10 },
    output: { fontSize: 22, lineHeight: 36, color: c.fg },
    rtl: { writingDirection: 'rtl', textAlign: 'right' },
    ltr: { writingDirection: 'ltr', textAlign: 'left' },
  });
