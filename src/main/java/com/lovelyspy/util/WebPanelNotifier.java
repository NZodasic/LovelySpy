package com.lovelyspy.util;

import com.google.gson.Gson;
import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.config.Config;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class WebPanelNotifier {
    private final LovelySpyPlugin plugin;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WebPanelNotifier(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendDetection(Logger.LogEntry entry) {
        Config config = plugin.getLovelyConfig();
        if (!config.webPanelEnabled) {
            return;
        }

        String url = config.webPanelUrl;
        String secret = config.webPanelSecret;

        if (url == null || url.isBlank() || url.equals("CHANGE_ME")) {
            return;
        }

        String body = gson.toJson(entry);
        SchedulerHelper.runTaskAsynchronously(plugin, () -> {
            try {
                URI endpoint = URI.create(url);
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("X-LovelySpy-Secret", secret)
                        .header("User-Agent", "LovelySpy-Plugin/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    plugin.getLogger().warning("Web panel API returned HTTP " + status + ".");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send web panel update: " + e.getMessage());
            }
        });
    }
}
