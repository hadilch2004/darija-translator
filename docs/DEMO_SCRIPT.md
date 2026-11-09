# 5-minute demo video: script

Record the screen with the microphone on (QuickTime on Mac, or any screen recorder). Rehearse once. Keep the terminal
font large. Before recording: start the service with your real Gemini key, load the extension, and have these ready:
a browser tab with an English paragraph, Postman (or a terminal) and the editor with the project open.

| Time | Show | Say |
|------|------|-----|
| 0:00–0:30 | README top and the deployment diagram (`docs/img/deployment-diagram.png`) | The problem: translate any text into Moroccan Darija with an LLM. One REST service, four clients. |
| 0:30–1:30 | **Code walkthrough, server.** `TranslatorResource.java`, then `BasicAuthFilter.java`, `TranslationService.java`, `GeminiClient.java`, `Prompts.java`. The class diagram on screen. | Jakarta REST resource with `translate()`; Basic auth filter and `@RolesAllowed`; validation; strategy interface `LlmClient` with Gemini, Ollama and mock back ends; the prompt and why user text is fenced. |
| 1:30–2:15 | **Run and test the service.** `./scripts/run-server.sh`. In Postman or cURL: health, a translation, then the same call without credentials (401) and with blank text (400). | Embedded Grizzly, one jar, configuration by environment variables. Errors are always JSON. |
| 2:15–3:30 | **Chrome extension.** Show `manifest.json` (side panel, context menu) and `background.js` briefly. Then on the English page: select a sentence, right-click, "Translate to Darija". Click 🔊 Read aloud. Press 🎤, say a sentence, hear the Darija. Switch to Latin letters in ⚙ and translate again. | Manifest V3, `chrome.sidePanel`; the selection is captured and pasted automatically; read aloud and voice-to-voice. |
| 3:30–4:15 | **Other clients.** Terminal: `python3 darija_client.py "Good morning"`. Browser: PHP page at `localhost:8000`. Phone or simulator: the React Native app translating and reading aloud. | Same endpoint, same credentials, different platforms. |
| 4:15–4:45 | `mvn test` output (and `npm test` in the extension) | Unit tests plus full-stack HTTP tests with a fake Gemini server; no API quota is used in tests. |
| 4:45–5:00 | The GitHub repository page | Limits: free-tier quotas, Darija spelling varies; next steps: HTTPS and a Jakarta Authentication module on Jetty. |

Tips

- Do a dry run first: the Gemini free tier can answer `429` if you test many times in a minute.
- If Chrome has no Moroccan Arabic voice, Read aloud falls back to another Arabic voice. Mention it rather than hiding it.
- The first microphone use needs the one-time **Allow microphone** step; do it before recording.
- Never show `server/.env` or your API key on screen.
