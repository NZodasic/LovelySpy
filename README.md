# LovelySpy 🕵️‍♂️

**LovelySpy** is an advanced, lightweight, and modern client/mod detection engine built for **Purpur 1.21.11** (supporting Folia). It utilizes multiple non-invasive detection vectors—including translation fingerprinting, client brand analysis, and plugin channel queries—to identify disallowed modifications and hacked clients without causing false positives.

It comes integrated with the native **PaperMC Dialog API** for real-time in-game configuration and an escalating punishment system.

---

## 🚀 Key Features

*   **Multi-Vector Detection Engine**:
    *   **Vector 1 (Translation Fingerprinting)**: Probes client translation support sequentially, confirms candidate results, and groups matching keys into one mod-level detection. `min_matches` can require multiple signatures before an action.
    *   **Vector 2 (Brand & Channel Analysis)**: Detects client identifiers sent during handshakes and queries listening plugin channels.
    *   **Vector 3 (Privacy Mod Detection)**: Detects chat signing bypasses (like NoChatReports), resource-pack spoofing, and confirmed key-resolution shielding used by OpSec/ExploitPreventer-style anti-fingerprinting tools.
    *   **Vector 4 (Resource Pack Alt Detection)**: Spots client resource pack status mismatches.
*   **Folia-Compatible Scheduling**: Thread-safe task execution using a custom scheduler utility supporting region-based multi-threading.
*   **In-Game Admin GUI (PaperMC Dialog API)**: Configure core delays, manage blacklisted/whitelisted brand lists, add new custom translation key detections, and choose response actions—all in-game.
*   **Escalating Ban System**: Auto-escalates ban durations for repeat offenses:
    1.  **1st Offense**: 15 Minutes
    2.  **2nd Offense**: 30 Minutes
    3.  **3rd Offense**: 1 Day
    4.  **4th Offense**: 3 Days
    5.  **5th+ Offense**: 30 Days
*   **JSON-based Logging**: Records all detections with granular metadata (UUID, Name, confidence levels, triggers, actions taken) in a clean `logs.json` file.

---

## 🛠️ Commands & Permissions

All administrative commands require permission. Below is the command index:

| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/lovelyspy gui` / `config` / `dialog` | Opens the interactive Dialog Configuration Menu | `lovelyspy.admin` | Op |
| `/lovelyspy check <player>` | Manually runs a translation probe on a player | `lovelyspy.check` | Op |
| `/lovelyspy info <player>` | Inspects client brand name and listening channels | `lovelyspy.check` | Op |
| `/lovelyspy list` | Lists online players with detected clients, loaders, and mods | `lovelyspy.check` | Op |
| `/lovelyspy offenses <player>` | Checks the current offense count for a player | `lovelyspy.check` | Op |
| `/lovelyspy resetoffense <player>` | Clears the recorded offenses back to 0 | `lovelyspy.reset` | Op |
| `/lovelyspy history <player>` | Displays the recent check logs of a player | `lovelyspy.check` | Op |
| `/lovelyspy alerts` | Toggles live in-game admin alert messages | `lovelyspy.alerts` | Op |
| `/lovelyspy reload` | Reloads `config.yml`, `mods.yml`, and `offenses.json` | `lovelyspy.reload` | Op |

---

## 📦 Default Detections (`mods.yml`)

The plugin comes pre-configured with detection rules for popular client packages and unfair mods:

*   **Hacked Clients (BAN)**: Meteor Client, Wurst Client, LiquidBounce, Aristois, BleachHack, Coffee Client, Lumina, Vape.
*   **Unfair Advantage Mods (KICK/BAN)**: KillAura (Fabric), AutoClicker (Fabric), Auto Clicker (p1k0chu), XRay (Fabric), ChestESP, Freecam, AutoTotem, Inventory Profiles Next, AutoFish, AutoSwitch, AntiAFK.
*   **Unallowed Utilities (KICK)**: World Downloader, Item Scroller, Xaero's Minimap, JourneyMap.
*   **Bypasses (BAN)**: OpSec / ExploitPreventer-style bypass protection. LovelySpy first records a failed vanilla control-key translation, then requires a separate confirmation probe over `key.forward`, `key.jump`, and `key.attack`; plain no-response remains inconclusive.

Meteor Client uses confirmed current keybind/category translations from its own
namespace. Existing installations that still contain the obsolete
`meteor-client.gui.tabs.mods` key are migrated automatically when the mod catalogue
loads. A confirmed mod produces one action and one offense regardless of how many
of its keys matched.

---

## 🔧 Building & Installation

### Requirements
*   **Java 25**
*   **Gradle 9.5+** or the included wrapper
*   **Purpur 1.21.11-R0.1-SNAPSHOT**

### Build the Plugin
To compile the plugin and generate the production `.jar` file, run:
```bash
./gradlew build
```
The output file will be saved at:
`build/libs/LovelySpy.jar`

### Install
1. Copy `LovelySpy.jar` to your Minecraft server's `plugins/` directory.
2. Restart the server.
3. Configure global settings in `plugins/LovelySpy/config.yml` and mod detections in `plugins/LovelySpy/mods.yml`, or use `/lovelyspy gui` in-game.
