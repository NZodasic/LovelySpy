package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.config.Config;
import com.lovelyspy.util.PacketHelper;
import com.lovelyspy.util.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Vector1_TranslationFingerprint {
    private final LovelySpyPlugin plugin;
    private final Map<UUID, ProbeSession> sessions = new ConcurrentHashMap<>();

    public Vector1_TranslationFingerprint(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isProbing(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public void probe(Player player) {
        probe(player, "Automatic");
    }

    public void probe(Player player, String checker) {
        probe(player, false, null, null, null, checker);
    }

    public void probe(Player player, boolean isConfirmation, List<String> specificKeys) {
        probe(player, isConfirmation, specificKeys, null, null, "Automatic");
    }

    public void probe(Player player, boolean isConfirmation, List<String> specificKeys, List<String> pendingKeys, List<String> flaggedKeys) {
        probe(player, isConfirmation, specificKeys, pendingKeys, flaggedKeys, "Automatic");
    }

    public void probe(Player player, boolean isConfirmation, List<String> specificKeys, List<String> pendingKeys,
                      List<String> flaggedKeys, String checker) {
        probePage(player, isConfirmation, specificKeys, pendingKeys, flaggedKeys, checker, null);
    }

    private void probePage(Player player, boolean isConfirmation, List<String> specificKeys,
                           List<String> pendingKeys, List<String> flaggedKeys, String checker,
                           Map<String, String> accumulatedResponses) {
        if (player.hasPermission("lovelyspy.bypass")) {
            return;
        }

        // Clean up any existing session
        cleanupSession(player.getUniqueId());

        Location loc = findAirBlock(player);
        if (loc == null) {
            plugin.getLogger().warning("Could not find safe air block to place virtual sign for " + player.getName());
            return;
        }

        // Build list of remaining keys to test
        Set<String> uniqueRemainingKeys = new LinkedHashSet<>();
        if (pendingKeys != null) {
            uniqueRemainingKeys.addAll(pendingKeys);
        } else {
            if (isConfirmation && specificKeys != null) {
                uniqueRemainingKeys.addAll(specificKeys);
            } else {
                // Add all keys from config
                for (Config.ModEntry entry : plugin.getLovelyConfig().modEntries.values()) {
                    if (entry.enabled && entry.keys != null) {
                        uniqueRemainingKeys.addAll(entry.keys);
                    }
                }
            }
        }
        List<String> remainingKeys = new ArrayList<>(uniqueRemainingKeys);

        // Extract up to 3 keys for this pass
        List<String> keysToTest = new ArrayList<>();
        for (int i = 0; i < 3 && !remainingKeys.isEmpty(); i++) {
            keysToTest.add(remainingKeys.remove(0));
        }

        // Pad or truncate to 3 lines (leaving line 4 for the canary key)
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (i < keysToTest.size()) {
                lines.add(keysToTest.get(i));
            } else {
                lines.add("");
            }
        }
        // Line 4 is the canary key
        lines.add(plugin.getLovelyConfig().canaryKey);

        // Send packets to the player. If LovelySpy cannot send the probe, do not create
        // a session that later times out as a false sign_packet_blocked detection.
        if (!PacketHelper.sendVirtualSign(player, loc, lines)) {
            plugin.getLogger().warning("Skipped translation probe for " + player.getName()
                    + " because the virtual sign packets could not be sent.");
            return;
        }

        ProbeSession session = new ProbeSession(player.getUniqueId(), player.getName(), loc, keysToTest, remainingKeys,
                flaggedKeys, isConfirmation, checker, accumulatedResponses);
        
        // A timeout is inconclusive: lag, packet filtering, or client protection can
        // all prevent a response. It must never be presented as a named hack.
        SchedulerHelper.LovelyTask timeoutTask = SchedulerHelper.runTaskLater(plugin, () -> {
            handleTimeout(player.getUniqueId());
        }, plugin.getLovelyConfig().probeTimeoutTicks);
        session.setTimeoutTask(timeoutTask);

        sessions.put(player.getUniqueId(), session);

        // Let the client render translations before closing. Closing in the same
        // packet tick corrupts responses on Lunar/Fabric and caused mass positives.
        SchedulerHelper.runTaskLater(plugin, () -> {
            if (sessions.get(player.getUniqueId()) == session && player.isOnline()) {
                PacketHelper.closeScreen(player);
            }
        }, 10L);
    }

    public void handleResponse(UUID uuid, String[] lines) {
        ProbeSession session = sessions.remove(uuid);
        if (session == null) return;

        if (session.getTimeoutTask() != null) {
            session.getTimeoutTask().cancel();
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        // Restore block client-side
        PacketHelper.restoreVirtualSign(player, session.getLocation());

        // Process lines
        List<String> testedKeys = session.getTestedKeys();
        Map<String, String> responses = new LinkedHashMap<>();
        List<String> newFlagged = new ArrayList<>();

        // 1. Check Canary key (Line 4 / Index 3)
        boolean canaryResolved = false;
        String canaryKey = plugin.getLovelyConfig().canaryKey;
        if (lines.length >= 4) {
            String response = lines[3] != null ? lines[3].trim() : "";
            if (!response.isEmpty() && !response.equals(canaryKey)) {
                canaryResolved = true;
            }
        }

        // 2. Check Mod keys (Lines 1-3)
        for (int i = 0; i < 3; i++) {
            if (i >= testedKeys.size() || i >= lines.length) continue;
            String key = testedKeys.get(i);
            if (key == null || key.isEmpty()) continue;

            String response = lines[i] != null ? lines[i].trim() : "";
            responses.put(key, response);

            if (!response.isEmpty() && !response.equals(key)) {
                newFlagged.add(key);
            }
        }
        session.addResponses(responses);
        Map<String, String> allResponses = new LinkedHashMap<>(session.getResponses());

        // If canary did not resolve, the translation shield is active (Vector 3)
        if (!canaryResolved) {
            plugin.getVector3().flagTranslationShield(player, session.getChecker());
        }

        // Accumulate flagged keys
        // Keep each signature only once. Duplicate keys in configuration must not
        // amplify one response into multiple detections or actions.
        Set<String> uniqueFlagged = new LinkedHashSet<>(session.getFlaggedKeys());
        uniqueFlagged.addAll(newFlagged);
        List<String> totalFlagged = new ArrayList<>(uniqueFlagged);

        // If there are more pending keys, continue to the next page
        if (!session.getAllPendingKeys().isEmpty()) {
            // Do not burst sign responses. Packet-funnel plugins commonly assign
            // elevated weight to sign updates; spacing pages prevents LovelySpy's
            // own probe from looking like packet spam.
            SchedulerHelper.runTaskLater(plugin, () -> {
                Player current = Bukkit.getPlayer(uuid);
                if (current != null && current.isOnline()) {
                    probe(current, session.isConfirmation(), null, session.getAllPendingKeys(),
                            totalFlagged, session.getChecker(), allResponses);
                }
            }, plugin.getLovelyConfig().probeBatchDelayTicks);
            return;
        }

        // No more keys to test. Proceed with actions
        if (totalFlagged.isEmpty()) {
            // Clean
            if (!session.isConfirmation()) {
                plugin.getLogger().info("Player " + player.getName() + " passed translation fingerprinting check.");
            }
            return;
        }

        // A single client cannot credibly expose a large collection of unrelated
        // mod translations at once. This is the characteristic result of a
        // malformed/automatically-closed sign response or packet incompatibility.
        // Never confirm or punish from such a scan.
        if (totalFlagged.size() > 3) {
            plugin.reportInconclusiveScan(player, totalFlagged, allResponses,
                    "Vector 1 (Invalid Mass-Positive Translation Scan)");
            return;
        }

        if (!session.isConfirmation()) {
            // Flagged during first pass, schedule confirmation pass after config delay
            long delayTicks = (plugin.getLovelyConfig().confirmationDelayMs * 20L) / 1000L;
            if (delayTicks < 1) delayTicks = 6L; // fallback to 300ms
            
            SchedulerHelper.runTaskLater(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    probe(p, true, totalFlagged, null, null, session.getChecker());
                }
            }, delayTicks);
        } else {
            // Confirmed. Group keys by configured mod so one installed mod produces
            // one action, not one ban/offense for every translation it exposes.
            executeConfirmedMods(player, totalFlagged, allResponses, session.getChecker());
        }
    }

    private void probe(Player player, boolean isConfirmation, List<String> specificKeys,
                       List<String> pendingKeys, List<String> flaggedKeys, String checker,
                       Map<String, String> accumulatedResponses) {
        probePage(player, isConfirmation, specificKeys, pendingKeys, flaggedKeys, checker,
                accumulatedResponses);
    }

    private void executeConfirmedMods(Player player, List<String> confirmedKeys,
                                      Map<String, String> responses, String checker) {
        for (Config.ModEntry entry : plugin.getLovelyConfig().modEntries.values()) {
            if (!entry.enabled || entry.keys.isEmpty()) continue;

            List<String> matches = TranslationSignatureMatcher.matchingKeys(
                    entry.keys, confirmedKeys);
            if (!TranslationSignatureMatcher.meetsThreshold(matches, entry.minMatches)) continue;

            Map<String, String> evidence = new LinkedHashMap<>();
            for (String key : matches) {
                evidence.put(key, responses.getOrDefault(key, "Resolved"));
            }
            plugin.getVector2().markConfirmedMod(player, entry.name);
            plugin.executeModDetection(player, entry, evidence,
                    "Vector 1 (Translation Fingerprinting)", checker);
        }
    }

    private void handleTimeout(UUID uuid) {
        ProbeSession session = sessions.remove(uuid);
        if (session == null) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        // Restore block client-side
        PacketHelper.restoreVirtualSign(player, session.getLocation());

        // Timeout means the client blocked the packets or did not respond (Vector 3: Privacy/Evasion)
        plugin.getVector3().flagSignTimeout(player, session.getChecker());
    }

    public void handleQuit(UUID uuid) {
        cleanupSession(uuid);
    }

    public void cleanup() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            cleanupSession(uuid);
        }
    }

    private void cleanupSession(UUID uuid) {
        ProbeSession session = sessions.remove(uuid);
        if (session != null) {
            if (session.getTimeoutTask() != null) {
                session.getTimeoutTask().cancel();
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                PacketHelper.restoreVirtualSign(player, session.getLocation());
            }
        }
    }

    public static Location findAirBlock(Player player) {
        Location loc = player.getLocation().clone();
        Location eyeLoc = player.getEyeLocation();
        // Check above player head first
        for (int dy = 2; dy <= 4; dy++) {
            Location test = eyeLoc.clone().add(0, dy, 0);
            if (test.getBlock().getType().isAir()) {
                return test;
            }
        }
        // Small area search
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Location test = loc.clone().add(dx, dy, dz);
                    if (test.getBlock().getType().isAir()) {
                        return test;
                    }
                }
            }
        }
        return loc.add(0, 3, 0); // fallback
    }
}
