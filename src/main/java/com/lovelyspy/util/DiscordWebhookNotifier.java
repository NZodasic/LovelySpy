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

public final class DiscordWebhookNotifier {
    private static final int DISCORD_EMBED_DESCRIPTION_LIMIT = 4096;
    private static final long CONFIG_WARNING_COOLDOWN_MS = 60_000L;

    private final LovelySpyPlugin plugin;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private long lastConfigWarningAt;

    public DiscordWebhookNotifier(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendDetection(Player player, String checker, Config.ModEntry matched, String key,
                               String responseVal, String vectorName, String confidence, String action) {
        Config config = plugin.getLovelyConfig();
        if (!config.discordWebhookEnabled) {
            return;
        }

        String webhookUrl = normalizeWebhookUrl(config.discordWebhookUrl);
        if (!isUsableWebhookUrl(webhookUrl)) {
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

        String description = config.discordWebhookMessage
                .replace("&name&", playerName)
                .replace("&checker&", checkerName)
                .replace("&reason&", reason)
                .replace("&hacks&", hacks)
                .replace("&results&", results);

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "LovelySpy Detection");
        embed.put("description", truncate(stripSectionColors(description), DISCORD_EMBED_DESCRIPTION_LIMIT));
        embed.put("color", clampDiscordColor(config.discordWebhookEmbedColor));
        embed.put("timestamp", Instant.now().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("embeds", List.of(embed));
        payload.put("allowed_mentions", Map.of("parse", List.of()));

        String body = gson.toJson(payload);
        SchedulerHelper.runTaskAsynchronously(plugin, () -> postWebhook(webhookUrl, body));
    }

    private void postWebhook(String webhookUrl, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + status + ".");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
        }
    }

    private String normalizeWebhookUrl(String webhookUrl) {
        if (webhookUrl == null) {
            return "";
        }

        String trimmed = webhookUrl.trim();
        if (trimmed.startsWith("[") && trimmed.contains("](") && trimmed.endsWith(")")) {
            int start = trimmed.indexOf("](") + 2;
            return trimmed.substring(start, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean isUsableWebhookUrl(String webhookUrl) {
        return webhookUrl.startsWith("https://discord.com/api/webhooks/")
                && !webhookUrl.contains("CHANGE_ME");
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
        plugin.getLogger().warning("Discord webhook is enabled, but webhook-url is missing or still set to CHANGE_ME.");
    }
}
