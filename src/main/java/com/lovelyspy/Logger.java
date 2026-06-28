package com.lovelyspy;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Logger {
    private final LovelySpyPlugin plugin;
    private final Gson gson = new Gson();
    private final List<LogEntry> inMemoryHistory = Collections.synchronizedList(new ArrayList<>());

    public Logger(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadHistoryFromFile();
    }

    public void log(UUID uuid, String username, List<String> keysTested, Map<String, String> responses,
                    List<String> vectorsTriggered, String confidence, String actionTaken) {
        
        LogEntry entry = new LogEntry();
        entry.uuid = uuid.toString();
        entry.username = username;
        entry.timestamp = System.currentTimeMillis();
        entry.keysTested = keysTested;
        entry.responses = responses;
        entry.vectorsTriggered = vectorsTriggered;
        entry.confidence = confidence;
        entry.actionTaken = actionTaken;

        inMemoryHistory.add(entry);
        
        // Write to log file asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File file = new File(plugin.getDataFolder(), "logs.json");
            file.getParentFile().mkdirs();
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(writer)) {
                bw.write(gson.toJson(entry));
                bw.newLine();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write log entry: " + e.getMessage());
            }
        });
    }

    public List<LogEntry> getHistory(String nameOrUuid) {
        List<LogEntry> result = new ArrayList<>();
        synchronized (inMemoryHistory) {
            for (LogEntry entry : inMemoryHistory) {
                if (entry.username.equalsIgnoreCase(nameOrUuid) || entry.uuid.equalsIgnoreCase(nameOrUuid)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    public List<LogEntry> getRecentLogs(int limit) {
        List<LogEntry> result = new ArrayList<>();
        synchronized (inMemoryHistory) {
            int size = inMemoryHistory.size();
            int start = Math.max(0, size - limit);
            for (int i = size - 1; i >= start; i--) {
                result.add(inMemoryHistory.get(i));
            }
        }
        return result;
    }

    private void loadHistoryFromFile() {
        inMemoryHistory.clear();
        File file = new File(plugin.getDataFolder(), "logs.json");
        if (!file.exists()) return;

        try (InputStreamReader isr = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    LogEntry entry = gson.fromJson(line, LogEntry.class);
                    if (entry != null) {
                        inMemoryHistory.add(entry);
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load logs.json: " + e.getMessage());
        }
    }

    public static class LogEntry {
        public String uuid;
        public String username;
        public long timestamp;
        public List<String> keysTested;
        public Map<String, String> responses;
        public List<String> vectorsTriggered;
        public String confidence;
        public String actionTaken;
    }
}
