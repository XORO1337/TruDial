# 🛡️ TruDial: Real-Time Scam Protection

![TruDial Banner](https://placehold.co/1200x400/0f172a/38bdf8?text=TruDial:+AI+Call+Protection)

**TruDial** is an advanced, offline-first Android application designed to protect users against digital arrests, social engineering, and phone scams using real-time AI call analysis. By leveraging a Local Large Language Model (LLM), TruDial guarantees complete privacy—your call data never leaves your device!

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android)](#)
[![Privacy](https://img.shields.io/badge/privacy-Offline_First-blue)](#)
[![License](https://img.shields.io/badge/license-MIT-green)](#)

---

## 🌟 Key Features

*   **🎙️ Real-Time Call Analysis:** Continuously monitors active calls to detect malicious intent or scam scripts (like fake Cybercell or digital arrest scenarios).
*   **🔒 Offline-First AI:** Powered by an on-device local LLM (Llama 2 via `llama.cpp`). No cloud dependencies, ensuring zero data leakage and total privacy.
*   **⚠️ Instant Scam Alerts:** Provides high-visibility warnings during a call if a high-risk conversation pattern is detected.
*   **📊 Security Dashboard:** Review your recent calls, blocked scams, and overall safety score in a sleek Material Design 3 interface.
*   **📝 One-Tap Reporting:** Easily report malicious numbers and view automatically generated incident reports.

---

## 📸 Screenshots

| Dashboard View | Active Call Analysis | Setup & Reporting |
|:---:|:---:|:---:|
| <img src="https://placehold.co/400x800/1e293b/cbd5e1?text=Dashboard\nOverview" width="200"> | <img src="https://placehold.co/400x800/1e293b/ef4444?text=Scam\nDetected\nWarning" width="200"> | <img src="https://placehold.co/400x800/1e293b/cbd5e1?text=Incident\nReport" width="200"> |

*Note: Screenshots are illustrative representations of the UI.*

---

## 🛠️ Technology Stack

*   **UI Framework:** Jetpack Compose (Material Design 3)
*   **Language:** Kotlin
*   **AI Engine:** Local LLM Integration (`llama.cpp` wrapper for Android)
*   **Local Storage:** Room Database for persistent call history and settings
*   **Architecture:** MVVM (Model-View-ViewModel)

---

## 🚀 Getting Started

### Prerequisites

*   Android Studio (Latest version)
*   Android SDK 36 (Minimum SDK 24)
*   A physical Android device or emulator with at least 4GB of RAM (for running the local LLM smoothly).

### Installation

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/your-username/trudial.git
    cd trudial
    ```

2.  **Environment Setup:**
    Create a `.env` file in the project root (you can copy from `.env.example`).
    ```bash
    cp .env.example .env
    ```
    *TruDial relies exclusively on local models, so no cloud API keys are required for core functionality!*

3.  **Build and Run:**
    Open the project in Android Studio, sync Gradle, and run the `app` module.

---

## 🔐 Privacy & Security

We believe your conversations belong to you. TruDial's AI processing is done **100% on-device**. 
*   **No audio recordings** are sent to remote servers.
*   **No transcriptions** are uploaded to third parties.

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
