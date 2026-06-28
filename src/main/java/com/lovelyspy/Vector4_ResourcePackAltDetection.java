package com.lovelyspy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Vector4_ResourcePackAltDetection {
    private final LovelySpyPlugin plugin;
    private final Map<UUID, PackFingerprint> fingerprints = new ConcurrentHashMap<>();

    public Vector4_ResourcePackAltDetection(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void recordPackLoaded(Player player, long durationMs) {
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        boolean loadedFromCache = durationMs < 300; // Less than 300ms means it loaded from local cache

        PackFingerprint fingerprint = new PackFingerprint(uuid.toString(), ip, durationMs, loadedFromCache);
        fingerprints.put(uuid, fingerprint);

        // Check for potential alts sharing the same IP and cache signature
        if (loadedFromCache && !ip.equals("unknown")) {
            for (Map.Entry<UUID, PackFingerprint> entry : fingerprints.entrySet()) {
                UUID otherUuid = entry.getKey();
                if (otherUuid.equals(uuid)) continue;

                PackFingerprint other = entry.getValue();
                if (other.ip.equals(ip) && other.loadedFromCache) {
                    Player otherPlayer = Bukkit.getPlayer(otherUuid);
                    String otherName = otherPlayer != null ? otherPlayer.getName() : other.uuid;
                    
                    // Flag as potential alts
                    plugin.executeDetection(player, "alt_detected:" + otherName, 
                            "Shares resource pack cache and IP with alt account " + otherName, 
                            "Vector 4 (Resource Pack Alt Detection)");
                }
            }
        }
    }

    public Map<UUID, PackFingerprint> getFingerprints() {
        return fingerprints;
    }

    public void cleanup() {
        fingerprints.clear();
    }

    public static class PackFingerprint {
        public final String uuid;
        public final String ip;
        public final long loadDurationMs;
        public final boolean loadedFromCache;
        public final long timestamp;

        public PackFingerprint(String uuid, String ip, long loadDurationMs, boolean loadedFromCache) {
            this.uuid = uuid;
            this.ip = ip;
            this.loadDurationMs = loadDurationMs;
            this.loadedFromCache = loadedFromCache;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
