# 🎙️ VoskLib

**VoskLib** is a high-performance, offline voice recognition library for **Minecraft Forge 1.20.1**. 
It provides a seamless bridge between the [Vosk Speech Recognition Engine](https://alphacephei.com/vosk/) and the Minecraft event bus,
allowing developers to create voice-activated gameplay, magic spells, or accessibility tools with minimal boilerplate.

## ✨ Features

* **100% Offline:** No API keys or internet connection required for recognition.
* **Dual Modes:** Switch between **Literal Mode** (wide vocabulary) and **Grammar Mode** (constrained lists).
* **In-Game Model Manager:** Download and manage Vosk models directly from the mod config menu with a built-in progress bar.
* **Thread Safe:** Background audio processing automatically synchronized with the Minecraft main thread.
* **Developer Friendly:** Easy-to-use events for partial and final speech results.
* **Dynamic Loading:** Requests the Alpha Cephei server for the latest models. 

---

## 🛠️ Installation for Users

1. Install **Minecraft Forge 1.20.1** from [Curseforge](https://www.curseforge.com/minecraft/mc-mods/vosklib).
2. Drop the `vosklib-1.0.jar` into your `mods` folder.
3. **Download a Model:**
* Open the game and go to **Mods** -> **VoskLib** -> **Config**.
* Select a model and click **Download**. 
* VoskLib will handle the download and extraction automatically.

---

## 💻 Developer API

To use VoskLib in your project, add it to your `build.gradle` and start listening for events.

### 1. Handling Speech Results

VoskLib fires events on the **Forge Event Bus**. These events are already executed on the **Main Thread**, so you can safely interact with the player or world.

```java
@SubscribeEvent
public void onVoiceResult(VoskVoiceEvent.Result event) {
    String text = event.getResult().toLowerCase();
    
    if (text.contains("fireball")) {
        // Your logic: Summon a fireball in front of the player
    }
}

@SubscribeEvent
public void onPartialSpeech(VoskVoiceEvent.Partial event) {
    // Useful for real-time UI subtitles
    String partial = event.getResult();
}

```

### 2. Controlling the State

You can request VoskLib to start or stop listening by posting a `VoskChangeRecognitionState` event.

```java
// Start Literal Mode (General Dictation)
MinecraftForge.EVENT_BUS.post(new VoskChangeRecognitionState(true));

// Start Grammar Mode (High Accuracy for specific words)
String[] spells = {"ignite", "freeze", "thunder", "heal"};
MinecraftForge.EVENT_BUS.post(new VoskChangeRecognitionState(true, spells));

// Stop Listening
MinecraftForge.EVENT_BUS.post(new VoskChangeRecognitionState(false));

```

---

## ⌨️ Keybinds

* **V (Default):** Toggles voice recognition on/off.
* *Configurable in the standard Controls menu under the "Vosk Voice Library" category.*

## 👋 Contribution

We welcome contributions\! As a project in early development, all contributions are valuable, from code implementation to bug reporting.

### How to Contribute

1.  **Report Issues:** Found a bug? Please open a detailed **Issue** on the repository. Include the full stacktrace and steps to reproduce.
2.  **Submit Code:** Fork the repository, create a descriptive branch, and submit a **Pull Request (PR)** with your changes. New features or bug fixes should align with the project's architectural principles.

### ⚖️ Licensing & Trademarks

This project is licensed under the **GNU GPL v3**. This ensures that the software remains free and open-source. Anyone who modifies or distributes this code must also share their source code under the same license.

**Trademark Notice:**
The names **VoskLib**, **Infinity Two Games**, and all associated logos are trademarks of Infinity Two Games. While the source code is open-source, this license does not grant you permission to use our brand names or logos for your own distributions or commercial products without express written consent.

## 📜 Credits

* **Vosk API:** Developed by [Alpha Cephei](https://alphacephei.com/vosk/).
* **Mod Author:** Infinity Two Games.
