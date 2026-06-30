package com.lovelyspy.util;

import com.google.gson.Gson;
import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.detection.ClientProfile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class Logger {
    private final LovelySpyPlugin plugin;
    private final Gson gson = new Gson();
    private final List<LogEntry> inMemoryHistory = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong nextCaseId = new AtomicLong(1);

    public Logger(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadHistoryFromFile();
    }

    public LogEntry log(UUID uuid, String username, List<String> keysTested, Map<String, String> responses,
                        List<String> vectorsTriggered, String confidence, String actionTaken) {
        
        LogEntry entry = new LogEntry();
        entry.caseId = nextCaseId.getAndIncrement();
        entry.uuid = uuid.toString();
        entry.username = username;
        entry.timestamp = System.currentTimeMillis();
        entry.keysTested = keysTested;
        entry.responses = responses;
        entry.vectorsTriggered = vectorsTriggered;
        entry.confidence = confidence;
        entry.actionTaken = actionTaken;

        // Retrieve client details
        String clientBrand = "Unknown";
        String loaders = "None";
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null && plugin.getVector2() != null) {
            ClientProfile profile = plugin.getVector2().getProfile(player);
            if (profile != null) {
                clientBrand = profile.client();
                loaders = profile.loaders().isEmpty() ? "None" : String.join(", ", profile.loaders());
            }
        }
        entry.clientBrand = clientBrand;
        entry.loaders = loaders;

        inMemoryHistory.add(entry);
        
        // Write to log file asynchronously
        SchedulerHelper.runTaskAsynchronously(plugin, () -> {
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
        return entry;
    }

    public int countToday(UUID uuid, String confidence) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        int count = 0;
        synchronized (inMemoryHistory) {
            for (LogEntry entry : inMemoryHistory) {
                if (entry.timestamp >= start.getTimeInMillis()
                        && entry.uuid.equalsIgnoreCase(uuid.toString())
                        && entry.confidence.equalsIgnoreCase(confidence)) {
                    count++;
                }
            }
        }
        return count;
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
        long highestCaseId = 0;
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
                        highestCaseId = Math.max(highestCaseId, entry.caseId);
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load logs.json: " + e.getMessage());
        }
        nextCaseId.set(highestCaseId + 1);
    }

    public static class LogEntry {
        public long caseId;
        public String uuid;
        public String username;
        public long timestamp;
        public List<String> keysTested;
        public Map<String, String> responses;
        public List<String> vectorsTriggered;
        public String confidence;
        public String actionTaken;
        public String clientBrand;
        public String loaders;
    }
}
