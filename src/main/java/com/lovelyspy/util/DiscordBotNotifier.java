package com.lovelyspy.util;

import com.google.gson.Gson;
import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.config.Config;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscordBotNotifier {
    private static final String DISCORD_API_BASE_URL = "https://discord.com/api/v10";
    private static final int DISCORD_EMBED_DESCRIPTION_LIMIT = 4096;
    private static final long CONFIG_WARNING_COOLDOWN_MS = 60_000L;

    private final LovelySpyPlugin plugin;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private long lastConfigWarningAt;

    public DiscordBotNotifier(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendDetection(Player player, String checker, Config.ModEntry matched, String key,
                               String responseVal, String vectorName, String confidence, String action) {
        Config config = plugin.getLovelyConfig();
        if (!config.discordBotEnabled) {
            return;
        }

        String botToken = normalizeBotToken(config.discordBotToken);
        String channelId = normalize(config.discordBotChannelId);
        if (!isUsableBotToken(botToken) || !isUsableChannelId(channelId)) {
            warnInvalidConfig();
            return;
        }

        String playerName = player.getName();
        String checkerName = checker == null || checker.isBlank() ? "Automatic" : checker;
        String reason = vectorName;
        String hacks = matched != null ? humanize(matched.name) : key;
        String results = key + " -> " + responseVal
                + "\nConfidence: " + confidence
                + "\nAction: " + action;

        String description = config.discordBotMessage
                .replace("&name&", playerName)
                .replace("&checker&", checkerName)
                .replace("&reason&", reason)
                .replace("&hacks&", hacks)
                .replace("&results&", results);

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "LovelySpy Detection");
        embed.put("description", truncate(stripSectionColors(description), DISCORD_EMBED_DESCRIPTION_LIMIT));
        embed.put("color", clampDiscordColor(config.discordBotEmbedColor));
        embed.put("timestamp", Instant.now().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("embeds", List.of(embed));
        payload.put("allowed_mentions", Map.of("parse", List.of()));

        String body = gson.toJson(payload);
        SchedulerHelper.runTaskAsynchronously(plugin, () -> postMessage(botToken, channelId, body));
    }

    private void postMessage(String botToken, String channelId, String body) {
        try {
            URI endpoint = URI.create(DISCORD_API_BASE_URL + "/channels/" + channelId + "/messages");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "DiscordBot (LovelySpy, 1.0.0)")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                plugin.getLogger().warning("Discord bot message returned HTTP " + status + ".");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Discord bot message: " + e.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeBotToken(String botToken) {
        String normalized = normalize(botToken);
        if (normalized.regionMatches(true, 0, "Bot ", 0, 4)) {
            return normalized.substring(4).trim();
        }
        return normalized;
    }

    private boolean isUsableBotToken(String botToken) {
        return !botToken.isBlank() && !botToken.contains("CHANGE_ME");
    }

    private boolean isUsableChannelId(String channelId) {
        return channelId.matches("\\d{17,20}") && !channelId.contains("CHANGE_ME");
    }

    private int clampDiscordColor(int color) {
        if (color < 0) {
            return 0;
        }
        return Math.min(color, 0xFFFFFF);
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }

        String[] parts = value.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    private String stripSectionColors(String value) {
        return value.replaceAll("(?i)\\u00A7[0-9A-FK-ORX]", "");
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void warnInvalidConfig() {
        long now = System.currentTimeMillis();
        if (now - lastConfigWarningAt < CONFIG_WARNING_COOLDOWN_MS) {
            return;
        }
        lastConfigWarningAt = now;
        plugin.getLogger().warning(
                "Discord bot alerts are enabled, but bot-token or channel-id is missing or invalid.");
    }
}
