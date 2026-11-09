const result = document.getElementById("result");

document.getElementById("allow").addEventListener("click", async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((track) => track.stop());
    result.textContent = "✔ Microphone allowed. You can close this tab and press Speak in the side panel.";
  } catch (error) {
    result.textContent = `Microphone was not allowed (${error.name}). Check the site settings for this extension in Chrome.`;
  }
});
