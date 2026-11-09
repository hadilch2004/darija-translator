// Service worker: context menu + keyboard shortcut. It never talks to the REST service itself; it
// just opens the side panel and hands it the selected text. The panel does the translating.

const MENU_ID = "translate-selection";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: MENU_ID,
    title: "Translate “%s” to Darija",
    contexts: ["selection"],
  });
  // Clicking the toolbar icon toggles the side panel.
  chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(console.error);
});

/** Leaves the text for the side panel (new or already open) to pick up and translate. */
function queueTranslation(text) {
  const trimmed = (text ?? "").trim();
  if (!trimmed) return Promise.resolve();
  return chrome.storage.session.set({ pending: { text: trimmed, nonce: Date.now() } });
}

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId !== MENU_ID || !tab?.id) return;
  // sidePanel.open() only works inside the user gesture, so call it before anything asynchronous.
  chrome.sidePanel.open({ tabId: tab.id }).catch(console.error);
  queueTranslation(info.selectionText).catch(console.error);
});

chrome.commands.onCommand.addListener((command, tab) => {
  if (command !== MENU_ID || !tab?.id) return;
  chrome.sidePanel.open({ tabId: tab.id }).catch(console.error);
  chrome.scripting
    .executeScript({ target: { tabId: tab.id }, func: () => String(window.getSelection() ?? "") })
    .then(([frame]) => queueTranslation(frame?.result))
    .catch(() => {
      // chrome:// pages and the Web Store cannot be scripted; the panel simply opens empty.
    });
});
