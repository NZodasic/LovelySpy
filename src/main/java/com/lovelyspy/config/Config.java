package com.lovelyspy.config;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

public class Config {
    private final LovelySpyPlugin plugin;
    private FileConfiguration yaml;

    public int probeDelayTicks;
    public int confirmationDelayMs;
    public int probeTimeoutTicks;
    public int probeBatchDelayTicks;
    public String canaryKey;
    public Map<String, ModEntry> modEntries = new HashMap<>();
    public List<String> knownCheatBrands;
    public List<String> knownCheatChannels;
    public List<String> legitimateBrands;
    public String logFile;
    public boolean discordBotEnabled;
    public String discordBotToken;
    public String discordBotChannelId;
    public int discordBotEmbedColor;
    public String discordBotMessage;

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
        parse();
    }

    public void reload() {
        plugin.reloadConfig();
        yaml = plugin.getConfig();
        parse();
    }

    public void save() {
        yaml.set("probe_delay_ticks", probeDelayTicks);
        yaml.set("confirmation_delay_ms", confirmationDelayMs);
        yaml.set("probe_timeout_ticks", probeTimeoutTicks);
        yaml.set("probe_batch_delay_ticks", probeBatchDelayTicks);
        yaml.set("canary_key", canaryKey);
        yaml.set("known_cheat_brands", knownCheatBrands);
        yaml.set("known_cheat_channels", knownCheatChannels);
        yaml.set("legitimate_brands", legitimateBrands);
        yaml.set("log_file", logFile);
        yaml.set("discord-bot.enabled", discordBotEnabled);
        yaml.set("discord-bot.bot-token", discordBotToken);
        yaml.set("discord-bot.channel-id", discordBotChannelId);
        yaml.set("discord-bot.embed-color", discordBotEmbedColor);
        yaml.set("discord-bot.message", discordBotMessage);
        
        yaml.set("actions.KICK", actionKickEnabled);
        yaml.set("actions.BAN", actionBanEnabled);
        yaml.set("actions.FLAG", actionFlagEnabled);
        yaml.set("actions.SHADOW", actionShadowEnabled);

        // Clear existing mods section first to prevent ghost entries
        yaml.set("mods", null);
        for (Map.Entry<String, ModEntry> entry : modEntries.entrySet()) {
            String path = "mods." + entry.getKey();
            ModEntry mod = entry.getValue();
            if (mod.keys != null) {
                yaml.set(path + ".keys", mod.keys);
            }
            if (mod.action != null) {
                yaml.set(path + ".action", mod.action);
            }
            if (mod.message != null) {
                yaml.set(path + ".message", mod.message);
            }
            if (mod.vector != null) {
                yaml.set(path + ".vector", mod.vector);
            }
        }
        plugin.saveConfig();
    }

    private void parse() {
        // Login produces a legitimate packet burst. Starting a sign probe inside
        // that burst can trip packet-funnel plugins before their VL has decayed.
        probeDelayTicks = Math.max(40, yaml.getInt("probe_delay_ticks", 40));
        confirmationDelayMs = yaml.getInt("confirmation_delay_ms", 300);
        probeTimeoutTicks = Math.max(20, yaml.getInt("probe_timeout_ticks", 60));
        probeBatchDelayTicks = Math.max(1, yaml.getInt("probe_batch_delay_ticks", 20));
        canaryKey = yaml.getString("canary_key", "key.forward");
        knownCheatBrands = yaml.getStringList("known_cheat_brands");
        knownCheatChannels = yaml.getStringList("known_cheat_channels");
        legitimateBrands = new ArrayList<>(yaml.getStringList("legitimate_brands"));
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

        actionKickEnabled = yaml.getBoolean("actions.KICK", true);
        actionBanEnabled = yaml.getBoolean("actions.BAN", true);
        actionFlagEnabled = yaml.getBoolean("actions.FLAG", true);
        actionShadowEnabled = yaml.getBoolean("actions.SHADOW", true);

        modEntries.clear();
        if (yaml.contains("mods")) {
            for (String key : yaml.getConfigurationSection("mods").getKeys(false)) {
                String path = "mods." + key;
                String action = yaml.getString(path + ".action", "FLAG");
                String message = yaml.getString(path + ".message", "Mod detected");
                String vector = yaml.getString(path + ".vector");
                List<String> keys = yaml.getStringList(path + ".keys");
                modEntries.put(key, new ModEntry(key, keys, action, message, vector));
            }
        }
    }

    public static class ModEntry {
        public final String name;
        public final List<String> keys;
        public final String action;
        public final String message;
        public final String vector;

        public ModEntry(String name, List<String> keys, String action, String message, String vector) {
            this.name = name;
            this.keys = keys;
            this.action = action;
            this.message = message;
            this.vector = vector;
        }
    }
}
