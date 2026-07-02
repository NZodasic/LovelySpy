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
    private static final int MOD_KEYS_PER_PAGE = 3;
    private static final int SIGN_LINES = 4;

    private final LovelySpyPlugin plugin;
    private final Map<UUID, ProbeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, PrivacyProbeEvidence> privacyCandidates = new ConcurrentHashMap<>();
    private final Set<UUID> privacyConfirmationsScheduled = ConcurrentHashMap.newKeySet();

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

        Deque<String> remainingKeys = new ArrayDeque<>(collectProbeKeys(isConfirmation, specificKeys, pendingKeys));

        // Extract up to three mod keys for this pass. The fourth sign line is
        // reserved for the vanilla privacy-control key.
        List<String> keysToTest = takeNextKeys(remainingKeys, MOD_KEYS_PER_PAGE);

        String controlKey = selectControlKey(accumulatedResponses);

        List<String> lines = paddedLines(keysToTest, MOD_KEYS_PER_PAGE);
        // Line 4 is a vanilla key that normal clients must resolve. If a client
        // echoes it back unchanged, keep it as candidate privacy-shield evidence.
        lines.add(controlKey);

        // Send packets to the player. If LovelySpy cannot send the probe, do not create
        // a session that later times out as a false sign_packet_blocked detection.
        if (!PacketHelper.sendVirtualSign(player, loc, lines)) {
            plugin.getLogger().warning("Skipped translation probe for " + player.getName()
                    + " because the virtual sign packets could not be sent.");
            return;
        }

        ProbeSession session = new ProbeSession(player.getUniqueId(), player.getName(), loc, keysToTest,
                new ArrayList<>(remainingKeys), flaggedKeys, isConfirmation, checker, accumulatedResponses,
                List.of(controlKey), null);
        
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
        }, plugin.getLovelyConfig().signCloseDelayTicks);
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

        // Check if response is suspiciously fast (GUI bypass / OpSec / ExploitPreventer)
        long duration = System.currentTimeMillis() - session.getStartTime();
        
        // Feed to Vector9 Behavioral Paradox Engine for latency analysis
        plugin.getVector9().recordSignProbeLatency(player, duration);
        
        long closeDelayMs = plugin.getLovelyConfig().signCloseDelayTicks * 50L;
        long threshold = Math.max(100L, closeDelayMs - 70L);
        if (duration < threshold && !player.hasPermission("lovelyspy.bypass")) {
            plugin.getVector3().flagSignGuiBypass(player, duration, session.getChecker());
            return;
        }

        if (session.isPrivacyProbe()) {
            handlePrivacyResponse(player, session, lines);
            return;
        }

        // Process lines
        List<String> testedKeys = session.getTestedKeys();
        Map<String, String> responses = new LinkedHashMap<>();
        List<String> newFlagged = new ArrayList<>();

        // 1. Check the vanilla control key (Line 4 / Index 3). This is only a
        // candidate signal here; LovelySpy requires a later confirmation probe
        // before mapping it to the OpSec/ExploitPreventer policy entry.
        PrivacyProbeEvidence controlEvidence = analyzeControlLines(session.getControlKeys(), lines, 3);
        recordPrivacyCandidate(uuid, controlEvidence);

        // 2. Check Mod keys (Lines 1-3)
        for (int i = 0; i < 3; i++) {
            if (i >= testedKeys.size() || i >= lines.length) continue;
            String key = testedKeys.get(i);
            if (key == null || key.isEmpty()) continue;

            String response = lines[i] != null ? lines[i].trim() : "";
            responses.put(key, response);

            // Feed to behavioral consistency detection
            long probeDuration = System.currentTimeMillis() - session.getStartTime();
            plugin.getVector7().recordTranslationProbe(player, key, response, probeDuration);

            if (!response.isEmpty() && !response.equals(key)) {
                newFlagged.add(key);
            }
        }
        session.addResponses(responses);
        Map<String, String> allResponses = new LinkedHashMap<>(session.getResponses());

        // Feed post-pack evaluation to Vector9 if we are in a post-pack probe context
        if (session.isPostPackProbe()) {
            plugin.getVector9().evaluatePostPackResponse(player, responses);
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

        // A single client cannot credibly expose a large collection of unrelated
        // mod translations at once. This is the characteristic result of a
        // malformed/automatically-closed sign response or packet incompatibility.
        // Never confirm or punish from such a scan.
        if (totalFlagged.size() > 3) {
            privacyCandidates.remove(uuid);
            privacyConfirmationsScheduled.remove(uuid);
            plugin.reportInconclusiveScan(player, totalFlagged, allResponses,
                    "Vector 1 (Invalid Mass-Positive Translation Scan)");
            return;
        }

        // No more keys to test. Proceed with actions.
        if (totalFlagged.isEmpty()) {
            schedulePrivacyConfirmationIfNeeded(player, session.getChecker());
            if (!session.isConfirmation()) {
                reportNoConfirmedSignatures(player, allResponses, session.getChecker());
            }
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
            schedulePrivacyConfirmationIfNeeded(player, session.getChecker());
        }
    }

    private void probe(Player player, boolean isConfirmation, List<String> specificKeys,
                       List<String> pendingKeys, List<String> flaggedKeys, String checker,
                       Map<String, String> accumulatedResponses) {
        probePage(player, isConfirmation, specificKeys, pendingKeys, flaggedKeys, checker,
                accumulatedResponses);
    }

    private List<String> collectProbeKeys(boolean isConfirmation, List<String> specificKeys,
                                          List<String> pendingKeys) {
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        if (pendingKeys != null) {
            addKeys(uniqueKeys, pendingKeys);
        } else if (isConfirmation && specificKeys != null) {
            addKeys(uniqueKeys, specificKeys);
        } else {
            for (Config.ModEntry entry : plugin.getLovelyConfig().modEntries.values()) {
                if (entry.enabled && entry.keys != null) {
                    addKeys(uniqueKeys, entry.keys);
                }
            }
        }
        return new ArrayList<>(uniqueKeys);
    }

    private void addKeys(Set<String> target, Collection<String> keys) {
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            target.add(key.trim());
        }
    }

    private List<String> takeNextKeys(Deque<String> pendingKeys, int maxKeys) {
        List<String> selected = new ArrayList<>(Math.max(0, maxKeys));
        for (int i = 0; i < maxKeys && !pendingKeys.isEmpty(); i++) {
            selected.add(pendingKeys.removeFirst());
        }
        return selected;
    }

    private List<String> paddedLines(List<String> values, int lineCount) {
        List<String> lines = new ArrayList<>(Math.max(0, lineCount));
        for (int i = 0; i < lineCount; i++) {
            lines.add(i < values.size() ? values.get(i) : "");
        }
        return lines;
    }

    private String selectControlKey(Map<String, String> accumulatedResponses) {
        List<String> controlKeys = plugin.getLovelyConfig().privacyControlKeys;
        if (controlKeys == null || controlKeys.isEmpty()) {
            return plugin.getLovelyConfig().canaryKey;
        }
        int pageIndex = accumulatedResponses == null ? 0
                : Math.max(0, accumulatedResponses.size() / MOD_KEYS_PER_PAGE);
        return controlKeys.get(pageIndex % controlKeys.size());
    }

    private void recordPrivacyCandidate(UUID uuid, PrivacyProbeEvidence evidence) {
        if (evidence == null || !evidence.hasUnresolved()) {
            return;
        }
        privacyCandidates.merge(uuid, evidence, PrivacyProbeEvidence::merge);
    }

    private void schedulePrivacyConfirmationIfNeeded(Player player, String checker) {
        UUID uuid = player.getUniqueId();
        PrivacyProbeEvidence candidate = privacyCandidates.get(uuid);
        if (candidate == null || !candidate.hasUnresolved()) {
            privacyCandidates.remove(uuid);
            return;
        }
        if (!privacyConfirmationsScheduled.add(uuid)) {
            return;
        }

        long delayTicks = (plugin.getLovelyConfig().confirmationDelayMs * 20L) / 1000L;
        if (delayTicks < 1) delayTicks = 6L;
        SchedulerHelper.runTaskLater(plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            PrivacyProbeEvidence currentCandidate = privacyCandidates.get(uuid);
            if (current == null || !current.isOnline()
                    || currentCandidate == null || !currentCandidate.hasUnresolved()) {
                privacyConfirmationsScheduled.remove(uuid);
                privacyCandidates.remove(uuid);
                return;
            }
            boolean started = probePrivacyPage(current,
                    new PrivacyProbeState(plugin.getLovelyConfig().privacyControlKeys), checker);
            if (!started) {
                privacyConfirmationsScheduled.remove(uuid);
                privacyCandidates.remove(uuid);
            }
        }, delayTicks);
    }

    private boolean probePrivacyPage(Player player, PrivacyProbeState state, String checker) {
        if (player.hasPermission("lovelyspy.bypass")) {
            return false;
        }
        if (state == null) {
            return false;
        }

        cleanupSession(player.getUniqueId());

        Location loc = findAirBlock(player);
        if (loc == null) {
            plugin.getLogger().warning("Could not find safe air block to place OpSec confirmation sign for "
                    + player.getName());
            return false;
        }

        if (!state.hasPending()) {
            finishPrivacyConfirmation(player, state, checker);
            return true;
        }

        List<String> keysToTest = state.takeBatch(SIGN_LINES);
        List<String> lines = paddedLines(keysToTest, SIGN_LINES);

        if (!PacketHelper.sendVirtualSign(player, loc, lines)) {
            plugin.getLogger().warning("Skipped OpSec confirmation probe for " + player.getName()
                    + " because the virtual sign packets could not be sent.");
            return false;
        }

        ProbeSession session = new ProbeSession(player.getUniqueId(), player.getName(), loc,
                List.of(), List.of(), List.of(), true, checker, Map.of(), keysToTest, state);
        SchedulerHelper.LovelyTask timeoutTask = SchedulerHelper.runTaskLater(plugin, () -> {
            handleTimeout(player.getUniqueId());
        }, plugin.getLovelyConfig().probeTimeoutTicks);
        session.setTimeoutTask(timeoutTask);
        sessions.put(player.getUniqueId(), session);

        SchedulerHelper.runTaskLater(plugin, () -> {
            if (sessions.get(player.getUniqueId()) == session && player.isOnline()) {
                PacketHelper.closeScreen(player);
            }
        }, plugin.getLovelyConfig().signCloseDelayTicks);
        return true;
    }

    private void handlePrivacyResponse(Player player, ProbeSession session, String[] lines) {
        PrivacyProbeState state = session.getPrivacyState();
        if (state == null) {
            return;
        }
        PrivacyProbeEvidence pageEvidence = analyzeControlLines(session.getControlKeys(), lines, 0);
        state.record(pageEvidence);

        if (state.hasPending()) {
            SchedulerHelper.runTaskLater(plugin, () -> {
                Player current = Bukkit.getPlayer(player.getUniqueId());
                if (current != null && current.isOnline()) {
                    boolean started = probePrivacyPage(current, state, session.getChecker());
                    if (!started) {
                        privacyConfirmationsScheduled.remove(player.getUniqueId());
                        privacyCandidates.remove(player.getUniqueId());
                    }
                } else {
                    privacyConfirmationsScheduled.remove(player.getUniqueId());
                    privacyCandidates.remove(player.getUniqueId());
                }
            }, plugin.getLovelyConfig().probeBatchDelayTicks);
            return;
        }

        finishPrivacyConfirmation(player, state, session.getChecker());
    }

    private void finishPrivacyConfirmation(Player player, PrivacyProbeState confirmationState,
                                           String checker) {
        UUID uuid = player.getUniqueId();
        privacyConfirmationsScheduled.remove(uuid);
        PrivacyProbeEvidence firstPass = privacyCandidates.remove(uuid);
        if (firstPass == null || firstPass.unresolvedKeys().isEmpty()
                || confirmationState == null || confirmationState.unresolvedKeys().isEmpty()) {
            return;
        }

        Set<String> repeated = confirmationState.repeatedUnresolvedKeys(firstPass.unresolvedKeys());
        if (repeated.isEmpty()) {
            return;
        }

        String evidence = repeated.stream()
                .map(key -> key + " stayed unresolved"
                        + " (initial=" + printableResponse(firstPass.responses().get(key))
                        + ", confirmation=" + printableResponse(confirmationState.responses().get(key)) + ")")
                .collect(java.util.stream.Collectors.joining("; "));
        plugin.getVector3().flagKeyResolutionShield(player, evidence, checker);
    }

    private PrivacyProbeEvidence analyzeControlLines(List<String> controlKeys, String[] lines, int firstLineIndex) {
        if (controlKeys == null || controlKeys.isEmpty()) {
            return PrivacyProbeEvidence.empty();
        }

        Set<String> unresolved = new LinkedHashSet<>();
        Map<String, String> responses = new LinkedHashMap<>();
        for (int i = 0; i < controlKeys.size(); i++) {
            String key = controlKeys.get(i);
            if (key == null || key.isBlank()) continue;
            int lineIndex = firstLineIndex + i;
            String response = "";
            if (lineIndex < lines.length && lines[lineIndex] != null) {
                response = lines[lineIndex].trim();
            }
            responses.put(key, response);
            if (response.isEmpty() || response.equalsIgnoreCase(key)) {
                unresolved.add(key);
            }
        }
        return new PrivacyProbeEvidence(unresolved, responses);
    }

    private String printableResponse(String response) {
        return response == null || response.isBlank() ? "<empty>" : response;
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

    private void reportNoConfirmedSignatures(Player player, Map<String, String> responses,
                                             String checker) {
        ClientProfile profile = plugin.getVector2().getProfile(player);
        boolean moddedEnvironment = !profile.loaders().isEmpty()
                || profile.brand().toLowerCase(Locale.ROOT).contains("fabric")
                || profile.brand().toLowerCase(Locale.ROOT).contains("forge");
        if (!moddedEnvironment) {
            plugin.getLogger().info("Player " + player.getName()
                    + " produced no confirmed translation signatures.");
            return;
        }

        Config.ModEntry strictPolicy = findVectorPolicy("unverifiable_client");
        String loaders = profile.loaders().isEmpty()
                ? "modded brand " + profile.brand()
                : String.join(", ", profile.loaders());
        String evidence = "No prohibited translation signature resolved across "
                + responses.size() + " responses in a " + loaders
                + " environment. This is compatible with either a clean modded client "
                + "or a correctly emulated OpSec/ExploitPreventer response.";
        if (strictPolicy != null && strictPolicy.enabled) {
            plugin.executeModDetection(
                    player,
                    strictPolicy,
                    Map.of("unverifiable_client", evidence),
                    "Vector 3 (Strict Unverifiable-Client Policy)",
                    checker);
            return;
        }
        plugin.getLogger().info("Player " + player.getName()
                + " produced no confirmed translation signatures, but the result is UNVERIFIABLE "
                + "against current key-resolution shields (" + loaders + ").");
    }

    private Config.ModEntry findVectorPolicy(String vector) {
        for (Config.ModEntry entry : plugin.getLovelyConfig().modEntries.values()) {
            if (entry.vector != null && entry.vector.equalsIgnoreCase(vector)) {
                return entry;
            }
        }
        return null;
    }

    private void handleTimeout(UUID uuid) {
        ProbeSession session = sessions.remove(uuid);
        if (session == null) return;

        privacyCandidates.remove(uuid);
        privacyConfirmationsScheduled.remove(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        // Restore block client-side
        PacketHelper.restoreVirtualSign(player, session.getLocation());

        // Timeout means the client blocked the packets or did not respond (Vector 3: Privacy/Evasion)
        plugin.getVector3().flagSignTimeout(player, session.getChecker());
    }

    public void handleQuit(UUID uuid) {
        cleanupSession(uuid);
        privacyCandidates.remove(uuid);
        privacyConfirmationsScheduled.remove(uuid);
    }

    public void cleanup() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            cleanupSession(uuid);
        }
        privacyCandidates.clear();
        privacyConfirmationsScheduled.clear();
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
