package com.lovelyspy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OffenseManager {
    private final LovelySpyPlugin plugin;
    private final Gson gson = new Gson();
    private final Map<String, Integer> offenses = new ConcurrentHashMap<>();
    private File file;

    public OffenseManager(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.file = new File(plugin.getDataFolder(), "offenses.json");
        load();
    }

    public int getOffenseCount(UUID uuid) {
        return offenses.getOrDefault(uuid.toString(), 0);
    }

    public int incrementOffenseCount(UUID uuid) {
        int count = getOffenseCount(uuid) + 1;
        offenses.put(uuid.toString(), count);
        save();
        return count;
    }

    public void setOffenseCount(UUID uuid, int count) {
        offenses.put(uuid.toString(), count);
        save();
    }

    public void clearOffenseCount(UUID uuid) {
        offenses.remove(uuid.toString());
        save();
    }

    public Date getBanExpiration(int offenseCount) {
        Calendar cal = Calendar.getInstance();
        int minutes = 15;
        if (offenseCount == 1) {
            minutes = 15;
        } else if (offenseCount == 2) {
            minutes = 30;
        } else if (offenseCount == 3) {
            minutes = 24 * 60; // 1 day
        } else if (offenseCount == 4) {
            minutes = 3 * 24 * 60; // 3 days
        } else if (offenseCount >= 5) {
            minutes = 30 * 24 * 60; // 30 days
        }
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    private void load() {
        if (!file.exists()) return;
        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            Map<String, Integer> map = gson.fromJson(br, new TypeToken<Map<String, Integer>>(){}.getType());
            if (map != null) {
                offenses.putAll(map);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load offenses.json: " + e.getMessage());
        }
    }

    private void save() {
        SchedulerHelper.runTaskAsynchronously(plugin, () -> {
            try {
                file.getParentFile().mkdirs();
                try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
                     BufferedWriter bw = new BufferedWriter(osw)) {
                    gson.toJson(offenses, bw);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save offenses.json: " + e.getMessage());
            }
        });
    }
}
