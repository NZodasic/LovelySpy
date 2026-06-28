package com.lovelyspy.config;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

public class Config {
    private final LovelySpyPlugin plugin;
    private FileConfiguration yaml;

    public int probeDelayTicks;
    public int confirmationDelayMs;
    public String canaryKey;
    public Map<String, ModEntry> modEntries = new HashMap<>();
    public List<String> knownCheatBrands;
    public List<String> knownCheatChannels;
    public List<String> legitimateBrands;
    public String logFile;
    public boolean discordWebhookEnabled;
    public String discordWebhookUrl;
    public int discordWebhookEmbedColor;
    public String discordWebhookMessage;

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
        yaml.set("canary_key", canaryKey);
        yaml.set("known_cheat_brands", knownCheatBrands);
        yaml.set("known_cheat_channels", knownCheatChannels);
        yaml.set("legitimate_brands", legitimateBrands);
        yaml.set("log_file", logFile);
        yaml.set("discord-webhook.enabled", discordWebhookEnabled);
        yaml.set("discord-webhook.webhook-url", discordWebhookUrl);
        yaml.set("discord-webhook.embed-color", discordWebhookEmbedColor);
        yaml.set("discord-webhook.message", discordWebhookMessage);
        
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
        probeDelayTicks = yaml.getInt("probe_delay_ticks", 15);
        confirmationDelayMs = yaml.getInt("confirmation_delay_ms", 300);
        canaryKey = yaml.getString("canary_key", "key.forward");
        knownCheatBrands = yaml.getStringList("known_cheat_brands");
        knownCheatChannels = yaml.getStringList("known_cheat_channels");
        legitimateBrands = yaml.getStringList("legitimate_brands");
        logFile = yaml.getString("log_file", "plugins/LovelySpy/logs.json");
        discordWebhookEnabled = yaml.getBoolean("discord-webhook.enabled", false);
        discordWebhookUrl = yaml.getString("discord-webhook.webhook-url", "https://discord.com/api/webhooks/CHANGE_ME");
        discordWebhookEmbedColor = yaml.getInt("discord-webhook.embed-color", 16776960);
        discordWebhookMessage = yaml.getString("discord-webhook.message",
                "**Player:** &name&\n**Checked by:** &checker&\n**Reason:** &reason&\n**Hacks checked:** &hacks&\n**Results:**\n&results&");

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
