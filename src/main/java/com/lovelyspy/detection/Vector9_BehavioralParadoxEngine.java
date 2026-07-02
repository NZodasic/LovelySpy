package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vector 9 — Behavioral Paradox Engine.
 *
 * Detects OpSec/ExploitPreventer by accumulating "behavioral paradoxes":
 * combinations of signals that are individually inconclusive but collectively
 * impossible for a genuine vanilla client.
 *
 * DSA Components:
 *   - BloomFilter<UUID, Signal>  : O(1) per-session signal deduplication
 *   - PriorityQueue<EvidenceNode>: Min-heap for TTL-based evidence expiry
 *   - WeightedParadoxGraph       : Adjacency-list DAG for scoring signal combinations
 *   - SlidingWindow<Long>        : ArrayDeque-based latency ring for Z-score analysis
 */
public final class Vector9_BehavioralParadoxEngine implements Listener {

    // ─── Signal Taxonomy ──────────────────────────────────────────────────────

    public enum Signal {
        // Brand/Channel paradoxes
        VANILLA_BRAND_NO_CHANNELS(3),       // Claims vanilla, registers zero mod channels
        VANILLA_BRAND_FABRIC_PROTOCOL(4),   // Claims vanilla but uses Fabric packet encodings
        CHANNEL_COUNT_ZERO_WITH_FABRIC(3),  // Fabric loader but zero channels at registration

        // Translation/Sign paradoxes
        SIGN_INSTANT_RESPONSE_VANILLA(5),   // <threshold ms response AND vanilla brand
        SIGN_FAST_NO_GUI(4),                // Response faster than server close-delay (existing, re-weighted)
        TRANSLATION_BLOCK_VANILLA(5),       // Vanilla brand but translation keys return raw fallback
        TRANSLATION_BLOCK_MODDED_ENV(2),    // Known mod key stayed raw in a modded environment

        // Chat paradoxes
        UNSIGNED_CHAT_PREMIUM_ACCOUNT(4),   // Premium account but unsigned messages

        // Resource pack paradoxes
        PACK_ACCEPT_STALE_TRANSLATIONS(5),  // Accepted pack but translations unchanged after accept
        PACK_INSTANT_ACCEPT(3),             // Already tracked by Vector 3, feeds into combinator

        // Known-packs paradoxes
        KNOWN_PACKS_VANILLA_COUNT_ONLY(4),  // Fabric client but only vanilla core pack in known-packs
        KNOWN_PACKS_EMPTY_FABRIC(5),        // Fabric client with zero known packs

        // Timing fingerprints
        PACKET_TIMING_BIMODALITY(1),        // Weak Vector 6 evidence; never sufficient alone
        NETTY_SIGN_RESPONSE_ANOMALY(6);     // Z-score > 2.5 on sign-response latency histogram

        public final int weight;
        Signal(int weight) { this.weight = weight; }
    }

    // ─── Paradox Combination Graph ─────────────────────────────────────────────
    //
    // Adjacency list: signal A + signal B together multiply by a bonus factor.
    // Modelled as Map<Signal, Map<Signal, Float>> where the float is a multiplier
    // applied to the sum of both weights when both are present.
    //
    // This is the "Weighted Paradox DAG" — edges represent statistically impossible
    // combinations; their weights amplify the score beyond the sum of parts.

    private static final Map<Signal, Map<Signal, Float>> PARADOX_GRAPH = new EnumMap<>(Signal.class);

    static {
        // P1: Instant sign response + vanilla brand = critical paradox
        addEdge(Signal.SIGN_INSTANT_RESPONSE_VANILLA, Signal.VANILLA_BRAND_NO_CHANNELS, 2.5f);
        addEdge(Signal.SIGN_FAST_NO_GUI,              Signal.VANILLA_BRAND_NO_CHANNELS, 2.0f);
        addEdge(Signal.SIGN_INSTANT_RESPONSE_VANILLA, Signal.TRANSLATION_BLOCK_VANILLA, 3.0f);

        // P2: Known-packs contradiction
        addEdge(Signal.KNOWN_PACKS_VANILLA_COUNT_ONLY, Signal.VANILLA_BRAND_NO_CHANNELS, 2.0f);
        addEdge(Signal.KNOWN_PACKS_EMPTY_FABRIC,       Signal.VANILLA_BRAND_FABRIC_PROTOCOL, 3.5f);

        // P3: Unsigned chat + premium
        addEdge(Signal.UNSIGNED_CHAT_PREMIUM_ACCOUNT, Signal.VANILLA_BRAND_NO_CHANNELS, 2.5f);
        addEdge(Signal.UNSIGNED_CHAT_PREMIUM_ACCOUNT, Signal.SIGN_FAST_NO_GUI,          2.0f);

        // P4: Pack accept → stale translations
        addEdge(Signal.PACK_ACCEPT_STALE_TRANSLATIONS, Signal.PACK_INSTANT_ACCEPT,     3.0f);
        addEdge(Signal.PACK_ACCEPT_STALE_TRANSLATIONS, Signal.VANILLA_BRAND_NO_CHANNELS, 2.5f);

        // P5: Netty latency anomaly with any other paradox
        addEdge(Signal.NETTY_SIGN_RESPONSE_ANOMALY, Signal.SIGN_INSTANT_RESPONSE_VANILLA, 4.0f);
        addEdge(Signal.NETTY_SIGN_RESPONSE_ANOMALY, Signal.VANILLA_BRAND_NO_CHANNELS,    2.5f);

        // Research timing side-channel: only amplify independent client-facing
        // contradictions. Inter-arrival bimodality remains weak evidence.
        addEdge(Signal.PACKET_TIMING_BIMODALITY, Signal.SIGN_INSTANT_RESPONSE_VANILLA, 1.5f);
        addEdge(Signal.PACKET_TIMING_BIMODALITY, Signal.TRANSLATION_BLOCK_VANILLA,     1.5f);
        addEdge(Signal.PACKET_TIMING_BIMODALITY, Signal.PACK_ACCEPT_STALE_TRANSLATIONS, 1.4f);
    }

    private static void addEdge(Signal a, Signal b, float multiplier) {
        PARADOX_GRAPH.computeIfAbsent(a, k -> new EnumMap<>(Signal.class)).put(b, multiplier);
        PARADOX_GRAPH.computeIfAbsent(b, k -> new EnumMap<>(Signal.class)).put(a, multiplier);
    }

    // ─── Evidence Node (used in the priority queue for TTL expiry) ────────────

    private record EvidenceNode(UUID uuid, Signal signal, long expiresAt)
            implements Comparable<EvidenceNode> {
        @Override
        public int compareTo(EvidenceNode o) {
            return Long.compare(this.expiresAt, o.expiresAt);
        }
    }

    // ─── Per-Player Bloom Filter (bitset-backed, 3 hash functions) ──────────

    /**
     * Lightweight Bloom filter for per-player signal deduplication.
     * Prevents the same signal from inflating the score within one session window.
     *
     * Uses k=3 hash functions over a 64-bit long (bit vector).
     * False-positive rate ≈ 0.8% for n=10 signals — acceptable for this use case.
     */
    private static final class SignalBloom {
        private long bits = 0L;

        private static int hash(Signal s, int seed) {
            long h = (long) s.ordinal() * 2654435761L + seed;
            return Math.abs((int) (h % 64));
        }

        public void add(Signal s) {
            bits |= (1L << hash(s, 1));
            bits |= (1L << hash(s, 31));
            bits |= (1L << hash(s, 97));
        }

        /** Returns true if this signal was probably already recorded this session. */
        public boolean mightContain(Signal s) {
            return ((bits >> hash(s, 1)) & 1) == 1
                && ((bits >> hash(s, 31)) & 1) == 1
                && ((bits >> hash(s, 97)) & 1) == 1;
        }

        public void clear() { bits = 0L; }
    }

    // ─── Sliding Window for Latency Z-Score (P5) ────────────────────────────

    /**
     * Fixed-capacity sliding window using ArrayDeque.
     * Stores sign-probe round-trip latencies (ms) for Z-score computation.
     *
     * Time complexity: O(1) add, O(n) mean/stddev — acceptable since n ≤ 20.
     */
    private static final class LatencyWindow {
        private static final int CAPACITY = 20;
        private final Deque<Long> window = new ArrayDeque<>(CAPACITY);

        public void add(long latencyMs) {
            if (window.size() == CAPACITY) window.pollFirst();
            window.addLast(latencyMs);
        }

        public int size() { return window.size(); }

        public double mean() {
            if (window.isEmpty()) return 0;
            long sum = 0;
            for (long v : window) sum += v;
            return (double) sum / window.size();
        }

        public double stddev() {
            if (window.size() < 2) return 1.0; // avoid division by zero
            double m = mean();
            double variance = 0;
            for (long v : window) variance += (v - m) * (v - m);
            return Math.sqrt(variance / window.size());
        }

        /**
         * Compute the Z-score of the most recently added latency.
         * Z = (x - μ) / σ
         * A negative Z-score means "faster than typical" — OpSec responses are
         * always faster (Netty thread, no game-tick hop).
         */
        public double zScore(long latencyMs) {
            double sigma = stddev();
            if (sigma < 1.0) return 0;
            return (latencyMs - mean()) / sigma;
        }
    }

    // ─── Per-Player State ─────────────────────────────────────────────────────

    private static final class PlayerEvidence {
        final SignalBloom bloom = new SignalBloom();
        final Map<Signal, Integer> signalCounts = new EnumMap<>(Signal.class);
        final Map<Signal, String> signalDetails = new EnumMap<>(Signal.class);
        final LatencyWindow latency = new LatencyWindow();
        // Tracks the translation key values before and after pack accept
        final Map<String, String> prePackTranslations = new LinkedHashMap<>();
        long lastSignProbeTime = -1;
        int signProbeCount = 0;
        boolean hasFiredThisSession = false;
    }

    // ─── Engine State ─────────────────────────────────────────────────────────

    private static final long EVIDENCE_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    private final LovelySpyPlugin plugin;
    private final Map<UUID, PlayerEvidence> evidenceMap = new ConcurrentHashMap<>();
    // Global min-heap for TTL expiry — processed on each signal intake
    private final PriorityBlockingQueue<EvidenceNode> expiryQueue = new PriorityBlockingQueue<>();
    private final AtomicInteger totalSignalsProcessed = new AtomicInteger(0);

    public Vector9_BehavioralParadoxEngine(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Public API: Signal Intake ────────────────────────────────────────────

    /**
     * Record a behavioral signal for a player.
     * O(1) average due to Bloom filter deduplication.
     * Triggers paradox score evaluation after intake.
     *
     * @param player the player under observation
     * @param signal the observed behavioral anomaly
     */
    public void recordSignal(Player player, Signal signal) {
        recordSignal(player, signal, null);
    }

    private void recordSignal(Player player, Signal signal, String detail) {
        if (player.hasPermission("lovelyspy.bypass")) return;

        UUID uuid = player.getUniqueId();
        PlayerEvidence evidence = evidenceMap.computeIfAbsent(uuid, k -> new PlayerEvidence());

        // Bloom filter dedup: same signal within session window only counted once
        if (evidence.bloom.mightContain(signal)) {
            return;
        }
        evidence.bloom.add(signal);
        evidence.signalCounts.merge(signal, 1, Integer::sum);
        if (detail != null && !detail.isBlank()) {
            evidence.signalDetails.put(signal, detail);
        }
        totalSignalsProcessed.incrementAndGet();

        // Schedule TTL expiry
        expiryQueue.offer(new EvidenceNode(uuid, signal, System.currentTimeMillis() + EVIDENCE_TTL_MS));

        // Drain expired entries (lazy expiry, O(log n))
        drainExpired();

        // Evaluate
        float score = computeParadoxScore(evidence.signalCounts);
        float fireThreshold = plugin.getLovelyConfig().vector9FireThreshold;
        if (evidence.signalCounts.size() >= 2
                && score >= fireThreshold
                && !evidence.hasFiredThisSession) {
            evidence.hasFiredThisSession = true;
            fireDetection(player, evidence, score, fireThreshold);
        }
    }

    /**
     * Records persistent per-packet-type timing bimodality from Vector 6.
     * This is intentionally a weak signal and requires independent Vector 9
     * evidence before the paradox engine can act.
     */
    public void recordTimingBimodality(Player player, String packetType,
                                       double coefficient, int sampleCount,
                                       int consecutiveWindows) {
        String detail = String.format(Locale.ROOT,
                "packet=%s BC=%.3f samples=%d persistent_windows=%d",
                packetType, coefficient, sampleCount, consecutiveWindows);
        recordSignal(player, Signal.PACKET_TIMING_BIMODALITY, detail);
    }

    /**
     * Record a sign-probe round-trip latency sample and emit P5 signal if anomalous.
     * Called from Vector1_TranslationFingerprint.handleResponse().
     *
     * @param player      the probed player
     * @param latencyMs   the measured response latency in milliseconds
     */
    public void recordSignProbeLatency(Player player, long latencyMs) {
        long closeDelayMs = plugin.getLovelyConfig().signCloseDelayTicks * 50L;
        recordSignProbeLatency(player, latencyMs,
                Math.max(100L, closeDelayMs - 70L));
    }

    public void recordSignProbeLatency(Player player, long latencyMs,
                                       long fastResponseThresholdMs) {
        if (player.hasPermission("lovelyspy.bypass")) return;

        UUID uuid = player.getUniqueId();
        PlayerEvidence evidence = evidenceMap.computeIfAbsent(uuid, k -> new PlayerEvidence());

        evidence.latency.add(latencyMs);
        evidence.signProbeCount++;

        // Need at least 5 samples for reliable Z-score estimation
        if (evidence.latency.size() < 5) return;

        double z = evidence.latency.zScore(latencyMs);
        // OpSec responses are always FASTER (more negative Z-score)
        // Threshold: z < -2.5 means the response is faster than 99.4% of normal responses
        if (z < -2.5) {
            recordSignal(player, Signal.NETTY_SIGN_RESPONSE_ANOMALY);
        }

        // Also emit P1 if brand is vanilla
        ClientProfile profile = plugin.getVector2().getProfile(player);
        if (profile != null
                && "vanilla".equalsIgnoreCase(profile.brand())
                && latencyMs < fastResponseThresholdMs) {
            recordSignal(player, Signal.SIGN_INSTANT_RESPONSE_VANILLA);
        }
    }

    /**
     * Called by Vector2 when brand + channel registration is analyzed.
     * Emits brand/channel paradox signals.
     */
    public void analyzeClientProfile(Player player, String brand, Set<String> channels) {
        if (player.hasPermission("lovelyspy.bypass")) return;

        boolean claimsVanilla = "vanilla".equalsIgnoreCase(brand);
        boolean hasZeroModChannels = channels.stream()
                .noneMatch(ch -> !ch.startsWith("minecraft:") && !ch.startsWith("fml:"));

        if (claimsVanilla && hasZeroModChannels) {
            recordSignal(player, Signal.VANILLA_BRAND_NO_CHANNELS);
        }

        // Detect Fabric protocol markers even with vanilla brand
        // Fabric API registers at least the "fabric:registry/sync/v1" channel on join
        // If the client brand says "vanilla" but REGISTER payload previously included
        // Fabric channels, that's a contradiction (now stripped by OpSec, but the timing
        // of the initial REGISTER vs the brand packet can reveal the ordering paradox)
        if (claimsVanilla && !hasZeroModChannels) {
            // Claims vanilla but we saw mod channels before the brand was spoofed
            recordSignal(player, Signal.VANILLA_BRAND_FABRIC_PROTOCOL);
        }
    }

    /**
     * Called after a resource pack is "loaded" (SUCCESSFULLY_LOADED status).
     * Schedules a translation re-probe to check for P4 (pack accept → stale translation).
     *
     * @param player       the player
     * @param packTranslationKeys  list of custom keys defined by the pushed resource pack
     */
    public void schedulePostPackTranslationProbe(Player player, List<String> packTranslationKeys) {
        if (packTranslationKeys == null || packTranslationKeys.isEmpty()) return;
        UUID uuid = player.getUniqueId();
        PlayerEvidence evidence = evidenceMap.computeIfAbsent(uuid, k -> new PlayerEvidence());
        // Store expected keys so the Vector1 response handler can compare
        for (String k : packTranslationKeys) {
            evidence.prePackTranslations.put(k, k); // raw key = "not resolved yet"
        }
    }

    /**
     * Called from Vector1.handleResponse() when a post-pack probe response arrives.
     * Checks if custom translation keys from the resource pack resolved to pack values
     * or remained as raw keys (indicating pack was not actually applied to language).
     *
     * @param player    the player
     * @param responses the key→value map from the sign response
     */
    public void evaluatePostPackResponse(Player player, Map<String, String> responses) {
        UUID uuid = player.getUniqueId();
        PlayerEvidence evidence = evidenceMap.get(uuid);
        if (evidence == null || evidence.prePackTranslations.isEmpty()) return;

        int staleCnt = 0;
        for (Map.Entry<String, String> entry : evidence.prePackTranslations.entrySet()) {
            String key = entry.getKey();
            String response = responses.get(key);
            // If the response is the raw key itself (unresolved), translation wasn't loaded
            if (key.equals(response) || response == null || response.isBlank()) {
                staleCnt++;
            }
        }

        // More than half of expected keys are stale → pack not truly loaded
        if (staleCnt > evidence.prePackTranslations.size() / 2) {
            recordSignal(player, Signal.PACK_ACCEPT_STALE_TRANSLATIONS);
        }

        evidence.prePackTranslations.clear();
    }

    /**
     * Called when ServerboundSelectKnownPacks analysis is complete.
     * On 1.21.11, Paper exposes the known-packs count via ClientConfiguration.
     * Emits P2 signals.
     *
     * @param player    the player
     * @param packCount number of pack entries the client declared
     * @param isFabric  whether the client's brand/loader signals Fabric
     */
    public void recordKnownPacks(Player player, int packCount, boolean isFabric) {
        if (!isFabric) return; // No paradox without Fabric context
        if (packCount == 0) {
            recordSignal(player, Signal.KNOWN_PACKS_EMPTY_FABRIC);
        } else if (packCount == 1) {
            // Only vanilla core pack — Fabric would always add at least fabric-api packs
            recordSignal(player, Signal.KNOWN_PACKS_VANILLA_COUNT_ONLY);
        }
    }

    // ─── Paradox Score Computation (Weighted DAG traversal) ──────────────────

    /**
     * Compute the weighted paradox score for the current signal set.
     *
     * Algorithm:
     *   1. Sum base weights of all present signals — O(|signals|)
     *   2. For each edge in PARADOX_GRAPH where both endpoints are present,
     *      add (weightA + weightB) * (multiplier - 1.0) as a bonus — O(|signals|²) worst case
     *      but bounded because |signals| ≤ Signal.values().length
     *
     * This is equivalent to a single BFS pass over the paradox DAG visiting
     * only nodes that are "active" (present in signalCounts).
     */
    private float computeParadoxScore(Map<Signal, Integer> presentSignals) {
        if (presentSignals.isEmpty()) return 0f;

        float score = 0f;
        Set<Signal> active = presentSignals.keySet();

        // Base weights
        for (Signal s : active) {
            score += s.weight;
        }

        // Edge bonuses — traverse adjacency list only for active signals
        Set<String> visitedEdges = new HashSet<>();
        for (Signal a : active) {
            Map<Signal, Float> neighbors = PARADOX_GRAPH.get(a);
            if (neighbors == null) continue;
            for (Map.Entry<Signal, Float> edge : neighbors.entrySet()) {
                Signal b = edge.getKey();
                if (!active.contains(b)) continue;
                // Canonical edge key to avoid counting edge a→b and b→a twice
                String edgeKey = Math.min(a.ordinal(), b.ordinal())
                        + ":" + Math.max(a.ordinal(), b.ordinal());
                if (!visitedEdges.add(edgeKey)) continue;
                float bonus = (a.weight + b.weight) * (edge.getValue() - 1.0f);
                score += bonus;
            }
        }
        return score;
    }

    // ─── Detection Firing ─────────────────────────────────────────────────────

    private void fireDetection(Player player, PlayerEvidence evidence, float score,
                               float fireThreshold) {
        StringBuilder evidenceStr = new StringBuilder();
        evidenceStr.append(String.format("Paradox score=%.1f (threshold=%.1f). Signals: ",
                score, fireThreshold));
        for (Map.Entry<Signal, Integer> entry : evidence.signalCounts.entrySet()) {
            evidenceStr.append(entry.getKey().name())
                    .append("×").append(entry.getValue()).append(" ");
            String detail = evidence.signalDetails.get(entry.getKey());
            if (detail != null) {
                evidenceStr.append("[").append(detail).append("] ");
            }
        }
        if (evidence.latency.size() >= 5) {
            evidenceStr.append(String.format("| latency_mean=%.1fms stddev=%.1fms",
                    evidence.latency.mean(), evidence.latency.stddev()));
        }

        plugin.getVector3().flagKeyResolutionShield(
                player,
                evidenceStr.toString(),
                "Vector 9 (Behavioral Paradox Engine)"
        );
    }

    // ─── TTL Expiry (lazy, using min-heap) ───────────────────────────────────

    /** Drain all expired EvidenceNodes from the priority queue. O(k log n) where k = expired count. */
    private void drainExpired() {
        long now = System.currentTimeMillis();
        while (true) {
            EvidenceNode head = expiryQueue.peek();
            if (head == null || head.expiresAt() > now) break;
            EvidenceNode expired = expiryQueue.poll();
            if (expired == null) break;
            PlayerEvidence evidence = evidenceMap.get(expired.uuid());
            if (evidence == null) continue;
            evidence.signalCounts.remove(expired.signal());
            evidence.signalDetails.remove(expired.signal());
            // Clear bloom bit is impossible (Bloom filters are append-only), so
            // we clear the entire bloom when all signals expire for a player.
            if (evidence.signalCounts.isEmpty()) {
                evidence.bloom.clear();
                evidence.hasFiredThisSession = false;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        evidenceMap.remove(uuid);
    }

    public void cleanup() {
        evidenceMap.clear();
        expiryQueue.clear();
    }
}
