# LovelySpy 🕵️‍♂️

**LovelySpy** is an advanced, lightweight, and modern client/mod detection engine built for **PaperMC 1.21.7+**. It utilizes multiple non-invasive detection vectors—including translation fingerprinting, client brand analysis, and plugin channel queries—to identify disallowed modifications and hacked clients without causing false positives.

It comes integrated with the experimental **PaperMC Dialog API** for real-time in-game configuration and an escalating punishment system.

---

## 🚀 Key Features

*   **Multi-Vector Detection Engine**:
    *   **Vector 1 (Translation Fingerprinting)**: Probes client translation support sequentially. Detects translation-shielding and registry-bypass mechanisms (e.g., OpSec Bypass) via dynamically tested canary keys.
    *   **Vector 2 (Brand & Channel Analysis)**: Detects client identifiers sent during handshakes and queries listening plugin channels.
    *   **Vector 3 (Privacy Mod Detection)**: Detects chat signing bypasses (like NoChatReports) and privacy tools.
    *   **Vector 4 (Resource Pack Alt Detection)**: Spots client resource pack status mismatches.
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
| `/lovelyspy offenses <player>` | Checks the current offense count for a player | `lovelyspy.check` | Op |
| `/lovelyspy resetoffense <player>` | Clears the recorded offenses back to 0 | `lovelyspy.reset` | Op |
| `/lovelyspy history <player>` | Displays the recent check logs of a player | `lovelyspy.check` | Op |
| `/lovelyspy alerts` | Toggles live in-game admin alert messages | `lovelyspy.alerts` | Op |
| `/lovelyspy reload` | Reloads `config.yml` and `offenses.json` | `lovelyspy.reload` | Op |

---

## 📦 Default Detections (`config.yml`)

The plugin comes pre-configured with detection rules for popular client packages and unfair mods:

*   **Hacked Clients (BAN)**: Meteor Client, Wurst Client, LiquidBounce, Aristois, BleachHack, Coffee Client, Lumina, Vape.
*   **Unfair Advantage Mods (KICK/BAN/FLAG)**: KillAura (Fabric), AutoClicker (Fabric), Auto Clicker (p1k0chu), XRay (Fabric), ChestESP, Freecam, AutoTotem, Inventory Profiles Next, AutoFish, AutoSwitch, AntiAFK.
*   **Unallowed Utilities (KICK/FLAG)**: World Downloader, Item Scroller, Xaero's Minimap, JourneyMap.
*   **Bypasses (SHADOW)**: OpSec Bypass (triggered by translation-shield check).

---

## 🔧 Building & Installation

### Requirements
*   **Java 21**
*   **Gradle 8.5+** or the included wrapper
*   **Paper 1.21.7-R0.1-SNAPSHOT** (or newer, supporting the Dialog API)

### Build the Plugin
To compile the plugin and generate the production `.jar` file, run:
```bash
./gradlew jar
```
The output file will be saved at:
`build/libs/LovelySpy.jar`

### Install
1. Copy `LovelySpy.jar` to your Minecraft server's `plugins/` directory.
2. Restart the server.
3. Configure the settings either using `/lovelyspy gui` in-game or by editing `plugins/LovelySpy/config.yml`.
