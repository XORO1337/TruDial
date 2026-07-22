# 🛡️ TruDial: Real-Time Scam Protection

![TruDial Banner](https://placehold.co/1200x400/0f172a/38bdf8?text=TruDial:+AI+Call+Protection)

**TruDial** is an Android call-protection app that detects **digital arrest**, authority-impersonation, and social-engineering scams in real time. It becomes your device's phone app, transcribes the live conversation, and scores it against a digital-arrest checklist using an LLM — either **on-device** (private) or a **cloud model** (Groq) that you enable with your own API key.

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android)](#)
[![License](https://img.shields.io/badge/license-MIT-green)](#)

---

## 🌟 Key Features

*   **📞 Default Phone App:** Implements an `InCallService`, so TruDial owns the in-call experience — its own **incoming-call screen** (Answer/Decline), **hold**, hang-up, and automatic **speakerphone** routing.
*   **🎙️ Live Transcription:** Transcribes the active call with **Groq Whisper** (`whisper-large-v3-turbo`) when a Groq key is configured, or the on-device Android `SpeechRecognizer` as a fallback. The transcript panel auto-scrolls as speech arrives.
*   **🧠 Hybrid AI Analysis:** Choose your engine:
    *   **On-device LLM** (MediaPipe LLM Inference) for high-RAM phones — fully private.
    *   **Cloud (Groq `llama-3.1-8b-instant`)** — selectable with your own Groq API key, even on capable phones.
*   **✅ Digital-Arrest Rubric:** Scores the whole conversation against a 7-point scam checklist and requires **multiple indicators** before raising risk, so a genuine call isn't flagged on a single line.
*   **🚫 Call Screening:** A `CallScreeningService` auto-blocks numbers with prior high-risk incidents.
*   **⚠️ Instant Alerts + Auto-Hold:** High-visibility warnings during the call, haptic feedback, and automatic hold when risk hits **Extreme**.
*   **📊 Security Dashboard:** Recent incidents, blocked scams, one-tap Cybercell reporting, and a **Scam Demo** button to exercise the full pipeline without a live call.

---

## 📸 Screenshots

| Dashboard View | Active Call Analysis | Setup & Reporting |
|:---:|:---:|:---:|
| <img src="https://placehold.co/400x800/1e293b/cbd5e1?text=Dashboard\nOverview" width="200"> | <img src="https://placehold.co/400x800/1e293b/ef4444?text=Scam\nDetected\nWarning" width="200"> | <img src="https://placehold.co/400x800/1e293b/cbd5e1?text=Incident\nReport" width="200"> |

*Note: Screenshots are illustrative representations of the UI.*

---

## 🛠️ Technology Stack

*   **UI:** Jetpack Compose (Material Design 3), MVVM
*   **Language:** Kotlin
*   **Telephony:** `InCallService` (default dialer) + `CallScreeningService`
*   **On-device AI:** MediaPipe GenAI LLM Inference
*   **Cloud AI:** Groq — `llama-3.1-8b-instant` (analysis) and `whisper-large-v3-turbo` (speech-to-text), via OkHttp
*   **Storage:** Room (call history / incidents) + DataStore Preferences (settings)
*   **Auth:** Firebase Auth (Google Sign-In)

---

## 🚀 Getting Started

### Prerequisites

*   Android Studio (latest)
*   Android SDK 36 (min SDK 24)
*   A physical device recommended (telephony features need a real phone stack)
*   ~7 GB+ RAM to run the on-device model; otherwise use the Groq cloud engine

### Installation

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/XORO1337/trudial.git
    cd trudial
    ```

2.  **Environment (`.env`):** Copy the example and adjust as needed. Build-config values are generated from `.env` (merged over `.env.example`) by the Secrets Gradle plugin.
    ```bash
    cp .env.example .env
    ```
    Keys of interest:
    *   `LOCAL_MODEL_URL` — on-device model download URL
    *   `TTS_API_KEY` — text-to-speech key (e.g. a Groq key)
    *   `AI_API_URL` / `AI_API_KEY` / `AI_MODEL` — default cloud fallback (OpenRouter)

3.  **Groq key (for cloud analysis + Whisper STT):** Enter it in the app during the **setup wizard** ("Choose AI Engine → Cloud (Groq)") or later under **Settings → AI Engine**. It is stored on-device in DataStore, not in the build.

4.  **Build & run:**
    ```bash
    ./gradlew :app:assembleDebug
    ```
    Open in Android Studio, sync Gradle, and run the `app` module.

5.  **Set as default phone app:** On first launch TruDial prompts for this. It's required — real-time screening, the incoming-call screen, and call hold only work when TruDial holds the phone/dialer role. Grant the requested permissions (phone, mic, call log, notifications).

---

## 🔐 Privacy & Security

TruDial has two modes, and they differ in where your call data goes:

*   **On-device mode:** transcription (Android `SpeechRecognizer`) and analysis (MediaPipe LLM) run locally; call audio and transcripts stay on your device.
*   **Cloud mode (Groq):** when you provide a Groq API key, call **audio is sent to Groq Whisper** for transcription and **transcript text is sent to Groq** for analysis. This is off-device processing — enable it only if you accept that trade-off. Nothing is sent to TruDial's own servers (there are none).

You choose the mode; the active engine is always shown in the logs (`Analysis engine selected: …`).

---

## ⚠️ Known Limitation: Call Audio Capture

Modern Android restricts third-party apps from capturing call audio (anti-recording policy). On many devices the microphone is muted to apps during a call, so live transcription may receive silence even with speaker on. This is a platform/OEM limitation, not an app bug. Use the **Scam Demo** button on the dashboard to run the full transcription → analysis → alert pipeline end-to-end regardless. Reliable live capture on all devices would require a VoIP/telephony-bridge architecture.

Besides that, live transcription has some issues with transcribing Hindi audio correctly. We aim to mitigate this issue by using stronger and more focused models in production.

---

## 🧪 Debugging

All call-flow events use a single log tag:

```bash
adb logcat -s TruDialCall
```

You'll see engine selection, per-window mic levels (RMS), transcript segments, each analysis verdict with the matched `indicators=[…]`, risk escalations, holds, and hang-ups.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. If you discover a bug or have a feature request, open an issue in the repository.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

<p align="center">Built with ❤️ for a safer digital world.</p>
