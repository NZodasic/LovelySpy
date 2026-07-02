package com.lovelyspy.config;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Config {
    private static final String LEGACY_METEOR_KEY = "meteor-client.gui.tabs.mods";
    private static final List<String> CURRENT_METEOR_KEYS = List.of(
            "key.meteor-client.open-gui",
            "key.meteor-client.open-commands",
            "key.category.meteor-client.meteor-client"
    );
    private static final Map<String, String> CURRENT_METEOR_EXPECTED_VALUES = Map.of(
            "key.meteor-client.open-gui", "Open GUI",
            "key.meteor-client.open-commands", "Open Commands",
            "key.category.meteor-client.meteor-client", "Meteor Client"
    );
    private static final int CURRENT_METEOR_MIN_MATCHES = 1;

    private final LovelySpyPlugin plugin;
    private FileConfiguration yaml;
    private File modsFile;
    private FileConfiguration modsYaml;

    public int probeDelayTicks;
    public int confirmationDelayMs;
    public int probeTimeoutTicks;
    public int probeBatchDelayTicks;
    public int signCloseDelayTicks;
    public double vector1SprtAlpha;
    public double vector1SprtBeta;
    public int vector1SprtMaxSamples;
    public int vector1SprtFastThresholdMs;
    public int vector1SprtSlowThresholdMs;
    public double vector1EwmaAlpha;
    public double vector1ChebyshevK;
    public int vector1AdaptiveMinimumSamples;
    public int vector1AdaptiveMinimumThresholdMs;
    public boolean opsecDetectionEnabled;
    public boolean opsecTimingBimodalityEnabled;
    public int opsecTimingMinimumSamples;
    public double opsecTimingBimodalityThreshold;
    public int opsecTimingBimodalityConsecutiveWindows;
    public int opsecMinimumObservationTimeSeconds;
    public float vector9FireThreshold;
    public boolean baritoneBehaviorEnabled;
    public int baritoneEvidenceWindowSeconds;
    public int baritoneMinimumCenteredTargets;
    public int baritoneMinimumMovementPatterns;
    public int baritoneMinimumGrimFlags;
    public int baritoneDetectionCooldownSeconds;
    public String canaryKey;
    public List<String> privacyControlKeys;
    public List<String> resourcePackProbeKeys;
    public boolean autoCheckOnJoinEnabled;
    public boolean autoCheckOnlyFirstJoin;
    public Map<String, ModEntry> modEntries = new LinkedHashMap<>();
    public List<String> knownCheatBrands;
    public List<String> knownCheatChannels;
    public List<String> legitimateBrands;
    public List<String> disabledCheatBrands;
    public List<String> disabledCheatChannels;
    public List<String> disabledLegitimateBrands;
    public String logFile;
    public boolean discordBotEnabled;
    public String discordBotToken;
    public String discordBotChannelId;
    public int discordBotEmbedColor;
    public String discordBotMessage;

    // Web Panel settings
    public boolean webPanelEnabled;
    public String webPanelUrl;
    public String webPanelSecret;

    // Global action toggles
    public boolean actionKickEnabled;
    public boolean actionBanEnabled;
    public boolean actionFlagEnabled;
    public boolean actionShadowEnabled;

    public Config(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        yaml = plugin.getConfig();
        loadModsConfig();
        parse();
    }

    public void reload() {
        plugin.reloadConfig();
        yaml = plugin.getConfig();
        loadModsConfig();
        parse();
    }

    public void save() {
        yaml.set("probe_delay_ticks", probeDelayTicks);
        yaml.set("confirmation_delay_ms", confirmationDelayMs);
        yaml.set("probe_timeout_ticks", probeTimeoutTicks);
        yaml.set("probe_batch_delay_ticks", probeBatchDelayTicks);
        yaml.set("sign_close_delay_ticks", signCloseDelayTicks);
        yaml.set("vector1.sprt.alpha", vector1SprtAlpha);
        yaml.set("vector1.sprt.beta", vector1SprtBeta);
        yaml.set("vector1.sprt.max_samples", vector1SprtMaxSamples);
        yaml.set("vector1.sprt.fast_threshold_ms", vector1SprtFastThresholdMs);
        yaml.set("vector1.sprt.slow_threshold_ms", vector1SprtSlowThresholdMs);
        yaml.set("vector1.adaptive_latency.ewma_alpha", vector1EwmaAlpha);
        yaml.set("vector1.adaptive_latency.chebyshev_k", vector1ChebyshevK);
        yaml.set("vector1.adaptive_latency.minimum_samples", vector1AdaptiveMinimumSamples);
        yaml.set("vector1.adaptive_latency.minimum_threshold_ms",
                vector1AdaptiveMinimumThresholdMs);
        yaml.set("opsec_detection.enabled", opsecDetectionEnabled);
        yaml.set("opsec_detection.features.timing_bimodality.enabled",
                opsecTimingBimodalityEnabled);
        yaml.set("opsec_detection.timing.min_samples", opsecTimingMinimumSamples);
        yaml.set("opsec_detection.features.timing_bimodality.threshold",
                opsecTimingBimodalityThreshold);
        yaml.set("opsec_detection.features.timing_bimodality.consecutive_windows",
                opsecTimingBimodalityConsecutiveWindows);
        yaml.set("opsec_detection.fpc.min_observation_time_sec",
                opsecMinimumObservationTimeSeconds);
        yaml.set("vector9.fire_threshold", vector9FireThreshold);
        yaml.set("baritone-behavior.enabled", baritoneBehaviorEnabled);
        yaml.set("baritone-behavior.evidence-window-seconds", baritoneEvidenceWindowSeconds);
        yaml.set("baritone-behavior.minimum-centered-targets", baritoneMinimumCenteredTargets);
        yaml.set("baritone-behavior.minimum-movement-patterns", baritoneMinimumMovementPatterns);
        yaml.set("baritone-behavior.minimum-grim-flags", baritoneMinimumGrimFlags);
        yaml.set("baritone-behavior.detection-cooldown-seconds", baritoneDetectionCooldownSeconds);
        privacyControlKeys = normalizePrivacyControlKeys(canaryKey, privacyControlKeys);
        canaryKey = privacyControlKeys.getFirst();
        yaml.set("canary_key", canaryKey);
        yaml.set("privacy_control_keys", privacyControlKeys);
        yaml.set("resource_pack_probe_keys", resourcePackProbeKeys);
        yaml.set("auto-check-on-join.enabled", autoCheckOnJoinEnabled);
        yaml.set("auto-check-on-join.only-first-join", autoCheckOnlyFirstJoin);
        yaml.set("known_cheat_brands", knownCheatBrands);
        yaml.set("known_cheat_channels", knownCheatChannels);
        yaml.set("legitimate_brands", legitimateBrands);
        yaml.set("disabled_cheat_brands", disabledCheatBrands);
        yaml.set("disabled_cheat_channels", disabledCheatChannels);
        yaml.set("disabled_legitimate_brands", disabledLegitimateBrands);
        yaml.set("log_file", logFile);
        yaml.set("discord-bot.enabled", discordBotEnabled);
        yaml.set("discord-bot.bot-token", discordBotToken);
        yaml.set("discord-bot.channel-id", discordBotChannelId);
        yaml.set("discord-bot.embed-color", discordBotEmbedColor);
        yaml.set("discord-bot.message", discordBotMessage);
        yaml.set("web-panel.enabled", webPanelEnabled);
        yaml.set("web-panel.url", webPanelUrl);
        yaml.set("web-panel.secret", webPanelSecret);
        
        yaml.set("actions.KICK", actionKickEnabled);
        yaml.set("actions.BAN", actionBanEnabled);
        yaml.set("actions.FLAG", actionFlagEnabled);
        yaml.set("actions.SHADOW", actionShadowEnabled);

        // Mods live in mods.yml. Clear old inline data from config.yml so the
        // split remains clean after GUI saves/reloads.
        yaml.set("mods", null);
        plugin.saveConfig();
        saveMods();
    }

    public void saveMods() {
        if (modsYaml == null || modsFile == null) {
            loadModsConfig();
        }
        modsYaml.set("mods", null);
        for (Map.Entry<String, ModEntry> entry : modEntries.entrySet()) {
            String path = "mods." + entry.getKey();
            ModEntry mod = entry.getValue();
            if (mod.keys != null) {
                modsYaml.set(path + ".keys", mod.keys);
            }
            modsYaml.set(path + ".expected_values",
                    mod.expectedValues.isEmpty() ? null : mod.expectedValues);
            modsYaml.set(path + ".min_matches", mod.minMatches);
            if (mod.action != null) {
                modsYaml.set(path + ".action", mod.action);
            }
            if (mod.message != null) {
                modsYaml.set(path + ".message", mod.message);
            }
            if (mod.vector != null) {
                modsYaml.set(path + ".vector", mod.vector);
            }
            modsYaml.set(path + ".enabled", mod.enabled);
        }
        try {
            modsFile.getParentFile().mkdirs();
            modsYaml.save(modsFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save mods.yml: " + exception.getMessage());
        }
    }

    private void loadModsConfig() {
        modsFile = new File(plugin.getDataFolder(), "mods.yml");
        boolean hasInlineMods = yaml != null && yaml.contains("mods");
        if (!modsFile.exists() && !hasInlineMods) {
            plugin.saveResource("mods.yml", false);
        }
        modsYaml = modsFile.exists() ? YamlConfiguration.loadConfiguration(modsFile) : new YamlConfiguration();

        if (hasInlineMods) {
            if (!modsYaml.contains("mods")) {
                ConfigurationSection oldMods = yaml.getConfigurationSection("mods");
                if (oldMods != null) {
                    copySection(oldMods, modsYaml, "mods");
                    saveModsFile();
                    plugin.getLogger().info("Migrated inline mod detections from config.yml to mods.yml.");
                }
            }
            yaml.set("mods", null);
            plugin.saveConfig();
        }
    }

    private void saveModsFile() {
        try {
            modsFile.getParentFile().mkdirs();
            modsYaml.save(modsFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save mods.yml: " + exception.getMessage());
        }
    }

    private void copySection(ConfigurationSection source, FileConfiguration target, String targetRoot) {
        for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) continue;
            target.set(targetRoot + "." + entry.getKey(), entry.getValue());
        }
    }

    private void parse() {
        // Login produces a legitimate packet burst. Starting a sign probe inside
        // that burst can trip packet-funnel plugins before their VL has decayed.
        probeDelayTicks = Math.max(40, yaml.getInt("probe_delay_ticks", 40));
        confirmationDelayMs = yaml.getInt("confirmation_delay_ms", 300);
        probeTimeoutTicks = Math.max(20, yaml.getInt("probe_timeout_ticks", 100));
        probeBatchDelayTicks = Math.max(1, yaml.getInt("probe_batch_delay_ticks", 20));
        signCloseDelayTicks = clamp(yaml.getInt("sign_close_delay_ticks", 3),
                3, Math.max(3, probeTimeoutTicks - 1));
        vector1SprtAlpha = clamp(yaml.getDouble("vector1.sprt.alpha", 0.05),
                0.001, 0.49);
        vector1SprtBeta = clamp(yaml.getDouble("vector1.sprt.beta", 0.10),
                0.001, 0.49);
        vector1SprtMaxSamples = clamp(
                yaml.getInt("vector1.sprt.max_samples", 20), 1, 100);
        vector1SprtSlowThresholdMs = clamp(
                yaml.getInt("vector1.sprt.slow_threshold_ms", 150), 2, 10_000);
        vector1SprtFastThresholdMs = clamp(
                yaml.getInt("vector1.sprt.fast_threshold_ms", 50),
                1, vector1SprtSlowThresholdMs - 1);
        vector1EwmaAlpha = clamp(
                yaml.getDouble("vector1.adaptive_latency.ewma_alpha", 0.3),
                0.01, 1.0);
        vector1ChebyshevK = clamp(
                yaml.getDouble("vector1.adaptive_latency.chebyshev_k", 3.5),
                0.1, 20.0);
        vector1AdaptiveMinimumSamples = clamp(
                yaml.getInt("vector1.adaptive_latency.minimum_samples", 5),
                2, 100);
        vector1AdaptiveMinimumThresholdMs = clamp(
                yaml.getInt("vector1.adaptive_latency.minimum_threshold_ms", 30),
                0, vector1SprtFastThresholdMs);
        opsecDetectionEnabled = yaml.getBoolean("opsec_detection.enabled", true);
        opsecTimingBimodalityEnabled = yaml.getBoolean(
                "opsec_detection.features.timing_bimodality.enabled", false);
        opsecTimingMinimumSamples = clamp(
                yaml.getInt("opsec_detection.timing.min_samples", 100),
                20, 1_000);
        opsecTimingBimodalityThreshold = clamp(
                yaml.getDouble(
                        "opsec_detection.features.timing_bimodality.threshold",
                        0.555),
                0.1, 1.0);
        opsecTimingBimodalityConsecutiveWindows = clamp(
                yaml.getInt(
                        "opsec_detection.features.timing_bimodality.consecutive_windows",
                        3),
                1, 10);
        opsecMinimumObservationTimeSeconds = clamp(
                yaml.getInt(
                        "opsec_detection.fpc.min_observation_time_sec",
                        30),
                5, 600);
        vector9FireThreshold = (float) Math.max(1.0,
                Math.min(100.0, yaml.getDouble("vector9.fire_threshold", 8.0)));
        baritoneBehaviorEnabled = yaml.getBoolean("baritone-behavior.enabled", true);
        baritoneEvidenceWindowSeconds = clamp(
                yaml.getInt("baritone-behavior.evidence-window-seconds", 120), 30, 600);
        baritoneMinimumCenteredTargets = clamp(
                yaml.getInt("baritone-behavior.minimum-centered-targets", 8), 3, 50);
        baritoneMinimumMovementPatterns = clamp(
                yaml.getInt("baritone-behavior.minimum-movement-patterns", 3), 1, 20);
        baritoneMinimumGrimFlags = clamp(
                yaml.getInt("baritone-behavior.minimum-grim-flags", 2), 1, 20);
        baritoneDetectionCooldownSeconds = clamp(
                yaml.getInt("baritone-behavior.detection-cooldown-seconds", 300), 30, 3600);
        canaryKey = yaml.getString("canary_key", "key.forward");
        privacyControlKeys = normalizePrivacyControlKeys(canaryKey,
                yaml.getStringList("privacy_control_keys"));
        canaryKey = privacyControlKeys.getFirst();
        resourcePackProbeKeys = yaml.getStringList("resource_pack_probe_keys");
        autoCheckOnJoinEnabled = yaml.getBoolean("auto-check-on-join.enabled", false);
        autoCheckOnlyFirstJoin = yaml.getBoolean("auto-check-on-join.only-first-join", false);
        knownCheatBrands = yaml.getStringList("known_cheat_brands");
        knownCheatChannels = yaml.getStringList("known_cheat_channels");
        legitimateBrands = new ArrayList<>(yaml.getStringList("legitimate_brands"));
        disabledCheatBrands = new ArrayList<>(yaml.getStringList("disabled_cheat_brands"));
        disabledCheatChannels = new ArrayList<>(yaml.getStringList("disabled_cheat_channels"));
        disabledLegitimateBrands = new ArrayList<>(yaml.getStringList("disabled_legitimate_brands"));
        // Older configs used Lunar's loader name, while current Lunar versions
        // identify themselves as "lunarclient:<version>,<loader>".
        boolean hasLunarLoader = legitimateBrands.stream()
                .anyMatch(brand -> brand.equalsIgnoreCase("lunarloader"));
        boolean hasLunarClient = legitimateBrands.stream()
                .anyMatch(brand -> brand.equalsIgnoreCase("lunarclient"));
        if (hasLunarLoader && !hasLunarClient) {
            legitimateBrands.add("lunarclient");
        }
        logFile = yaml.getString("log_file", "plugins/LovelySpy/logs.json");
        discordBotEnabled = yaml.getBoolean("discord-bot.enabled", false);
        discordBotToken = yaml.getString("discord-bot.bot-token", "CHANGE_ME");
        discordBotChannelId = yaml.getString("discord-bot.channel-id", "CHANGE_ME");
        discordBotEmbedColor = yaml.getInt("discord-bot.embed-color", 16776960);
        String legacyDiscordMessage =
                "**Player:** &name&\n**Checked by:** &checker&\n**Reason:** &reason&\n"
                        + "**Hacks checked:** &hacks&\n**Results:**\n&results&";
        discordBotMessage = yaml.getString("discord-bot.message",
                "&summary&");
        if (discordBotMessage.equals(legacyDiscordMessage)) {
            discordBotMessage = "&summary&";
        }

        webPanelEnabled = yaml.getBoolean("web-panel.enabled", false);
        webPanelUrl = yaml.getString("web-panel.url", "http://localhost:3000/api/detections");
        webPanelSecret = yaml.getString("web-panel.secret", "YOUR_CUSTOM_SECRET_KEY");
        if (webPanelSecret == null || webPanelSecret.isEmpty() || webPanelSecret.equals("YOUR_CUSTOM_SECRET_KEY") || webPanelSecret.equals("CHANGE_ME")) {
            webPanelSecret = generateSecureSecret();
            yaml.set("web-panel.secret", webPanelSecret);
            plugin.saveConfig();
        }

        actionKickEnabled = yaml.getBoolean("actions.KICK", true);
        actionBanEnabled = yaml.getBoolean("actions.BAN", true);
        actionFlagEnabled = yaml.getBoolean("actions.FLAG", true);
        actionShadowEnabled = yaml.getBoolean("actions.SHADOW", true);

        modEntries.clear();
        boolean migratedSignatures = ensureBuiltInPolicyEntries();
        if (modsYaml.contains("mods")) {
            for (String key : modsYaml.getConfigurationSection("mods").getKeys(false)) {
                String path = "mods." + key;
                String action = modsYaml.getString(path + ".action", "FLAG");
                String message = modsYaml.getString(path + ".message", "Mod detected");
                String vector = modsYaml.getString(path + ".vector");
                List<String> keys = new ArrayList<>(modsYaml.getStringList(path + ".keys"));
                Map<String, String> expectedValues = readStringMap(path + ".expected_values");
                boolean migratedMeteor = keys.removeIf(
                        signature -> signature.equalsIgnoreCase(LEGACY_METEOR_KEY));
                if (migratedMeteor) {
                    for (String signature : CURRENT_METEOR_KEYS) {
                        if (keys.stream().noneMatch(existing -> existing.equalsIgnoreCase(signature))) {
                            keys.add(signature);
                        }
                    }
                    modsYaml.set(path + ".keys", keys);
                    modsYaml.set(path + ".min_matches", CURRENT_METEOR_MIN_MATCHES);
                    expectedValues = new LinkedHashMap<>(CURRENT_METEOR_EXPECTED_VALUES);
                    modsYaml.set(path + ".expected_values", expectedValues);
                    migratedSignatures = true;
                    plugin.getLogger().info("Migrated the obsolete Meteor Client translation signature "
                            + "to the current multi-key fingerprint.");
                }
                int defaultMinMatches = migratedMeteor ? CURRENT_METEOR_MIN_MATCHES : 1;
                int minMatches = modsYaml.getInt(path + ".min_matches", defaultMinMatches);
                if (key.equalsIgnoreCase("meteor_client")
                        && containsAllIgnoreCase(keys, CURRENT_METEOR_KEYS)
                        && minMatches > CURRENT_METEOR_MIN_MATCHES) {
                    minMatches = CURRENT_METEOR_MIN_MATCHES;
                    modsYaml.set(path + ".min_matches", CURRENT_METEOR_MIN_MATCHES);
                    migratedSignatures = true;
                    plugin.getLogger().info("Updated Meteor Client translation detection to require one "
                            + "confirmed current Meteor key. The Meteor namespace is unique, and the "
                            + "probe still requires confirmation before action.");
                }
                if (key.equalsIgnoreCase("meteor_client") && expectedValues.isEmpty()
                        && containsAllIgnoreCase(keys, CURRENT_METEOR_KEYS)) {
                    expectedValues = new LinkedHashMap<>(CURRENT_METEOR_EXPECTED_VALUES);
                    modsYaml.set(path + ".expected_values", expectedValues);
                    migratedSignatures = true;
                    plugin.getLogger().info("Added expected Meteor Client translation values "
                            + "for key-resolution shield correlation.");
                }
                boolean enabled = modsYaml.getBoolean(path + ".enabled", true);
                modEntries.put(key, new ModEntry(key, keys, expectedValues, minMatches,
                        action, message, vector, enabled));
            }
        }
        if (migratedSignatures) {
            saveModsFile();
        }
    }

    private boolean ensureBuiltInPolicyEntries() {
        boolean changed = false;
        if (!modsYaml.contains("mods.opsec")) {
            String path = "mods.opsec";
            modsYaml.set(path + ".keys", List.of());
            modsYaml.set(path + ".vector", "privacy_probe");
            modsYaml.set(path + ".action", "BAN");
            modsYaml.set(path + ".message",
                    "Bypass Detected: OpSec / ExploitPreventer-style key-resolution protection");
            modsYaml.set(path + ".enabled", true);
            changed = true;
        }
        if (!modsYaml.contains("mods.unverifiable_modded_client")) {
            String path = "mods.unverifiable_modded_client";
            modsYaml.set(path + ".keys", List.of());
            modsYaml.set(path + ".vector", "unverifiable_client");
            modsYaml.set(path + ".action", "BAN");
            modsYaml.set(path + ".message",
                    "Unverifiable modded client: prohibited-mod scan may be shielded");
            modsYaml.set(path + ".enabled", false);
            changed = true;
        }
        if (!modsYaml.contains("mods.baritone")) {
            String path = "mods.baritone";
            modsYaml.set(path + ".keys", List.of());
            modsYaml.set(path + ".vector", "baritone_behavior");
            modsYaml.set(path + ".action", "FLAG");
            modsYaml.set(path + ".message",
                    "Baritone-like automation detected (behavior + Grim correlation)");
            modsYaml.set(path + ".enabled", true);
            changed = true;
        }
        if (changed) {
            plugin.getLogger().info("Added missing built-in detection policies to mods.yml "
                    + "without changing existing entries.");
        }
        return changed;
    }

    public ModEntry findPolicy(String preferredName, String vector) {
        if (preferredName != null) {
            ModEntry exact = modEntries.get(preferredName);
            if (exact != null) {
                return exact;
            }
            for (Map.Entry<String, ModEntry> entry : modEntries.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(preferredName)) {
                    return entry.getValue();
                }
            }
        }
        if (vector != null) {
            for (ModEntry entry : modEntries.values()) {
                if (entry.vector != null && entry.vector.equalsIgnoreCase(vector)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private boolean containsAllIgnoreCase(List<String> values, List<String> required) {
        for (String expected : required) {
            boolean found = values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
            if (!found) return false;
        }
        return true;
    }

    private Map<String, String> readStringMap(String path) {
        return readStringMap(modsYaml.getConfigurationSection(path));
    }

    static Map<String, String> readStringMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        // Bukkit expands dots in map keys into nested sections. Reading recursive
        // leaf paths reconstructs the original translation keys after save/reload.
        for (Map.Entry<String, Object> entry : section.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String value && !value.isBlank()) {
                values.put(entry.getKey(), value);
            }
        }
        return values;
    }

    public String getExpectedValue(String key) {
        if (key == null) {
            return null;
        }
        for (ModEntry entry : modEntries.values()) {
            for (Map.Entry<String, String> expected : entry.expectedValues.entrySet()) {
                if (expected.getKey().equalsIgnoreCase(key)) {
                    return expected.getValue();
                }
            }
        }
        return null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private String generateSecureSecret() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private List<String> normalizePrivacyControlKeys(String primaryCanary, List<String> configuredKeys) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addControlKey(keys, primaryCanary);
        if (configuredKeys != null) {
            for (String key : configuredKeys) {
                addControlKey(keys, key);
            }
        }
        addControlKey(keys, "key.forward");
        addControlKey(keys, "key.jump");
        addControlKey(keys, "key.attack");
        return new ArrayList<>(keys);
    }

    private void addControlKey(Set<String> keys, String key) {
        if (key == null) return;
        String cleaned = key.trim();
        if (!cleaned.isEmpty()) {
            keys.add(cleaned);
        }
    }

    public static class ModEntry {
        public final String name;
        public final List<String> keys;
        public final Map<String, String> expectedValues;
        public final int minMatches;
        public final String action;
        public final String message;
        public final String vector;
        public final boolean enabled;

        public ModEntry(String name, List<String> keys, String action, String message, String vector) {
            this(name, keys, Map.of(), 1, action, message, vector, true);
        }

        public ModEntry(String name, List<String> keys, String action, String message, String vector,
                        boolean enabled) {
            this(name, keys, Map.of(), 1, action, message, vector, enabled);
        }

        public ModEntry(String name, List<String> keys, int minMatches, String action, String message,
                        String vector, boolean enabled) {
            this(name, keys, Map.of(), minMatches, action, message, vector, enabled);
        }

        public ModEntry(String name, List<String> keys, Map<String, String> expectedValues,
                        int minMatches, String action, String message, String vector,
                        boolean enabled) {
            this.name = name;
            this.keys = keys != null ? List.copyOf(keys) : List.of();
            this.expectedValues = expectedValues != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(expectedValues))
                    : Map.of();
            this.minMatches = Math.max(1, Math.min(minMatches,
                    this.keys.isEmpty() ? 1 : this.keys.size()));
            this.action = action;
            this.message = message;
            this.vector = vector;
            this.enabled = enabled;
        }
    }
}
