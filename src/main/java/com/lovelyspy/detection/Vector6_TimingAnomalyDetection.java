package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.config.Config;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vector 6: Timing Anomaly Detection
 * 
 * Uses sliding window statistical analysis to detect packet filtering anomalies
 * caused by OpSec's selective blocking mechanisms.
 * 
 * Algorithm: Sliding Window with Statistical Anomaly Detection
 * - Maintains sliding window of packet timing samples
 * - Computes statistical metrics (mean, std dev, z-score)
 * - Detects anomalies using z-score thresholding
 * - Uses exponential moving average for trend analysis
 */
public final class Vector6_TimingAnomalyDetection {
    
    // Sliding window configuration
    private static final int WINDOW_SIZE = 50;
    private static final double Z_SCORE_THRESHOLD = 3.0;
    private static final double EMA_ALPHA = 0.2;
    private static final int ANOMALIES_REQUIRED = 8;
    private static final long ANOMALY_BURST_WINDOW_MS = 10_000L;
    private static final long ALERT_COOLDOWN_MS = 120_000L;
    
    private final LovelySpyPlugin plugin;
    private final Map<UUID, TimingWindow> timingWindows = new ConcurrentHashMap<>();
    
    public Vector6_TimingAnomalyDetection(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Records a packet timing sample for anomaly detection
     */
    public void recordPacketTiming(Player player, String packetType, long durationNanos) {
        if (player.hasPermission("lovelyspy.bypass") || !isTimingDetectionEnabled()) return;
        
        UUID uuid = player.getUniqueId();
        TimingWindow window = timingWindows.computeIfAbsent(uuid, k -> new TimingWindow());
        
        window.addSample(packetType, durationNanos);
        
        // Check for anomalies after sufficient samples
        if (window.getSampleCount() >= WINDOW_SIZE) {
            AnomalyResult result = window.detectAnomalies();
            AlertGate.Decision decision =
                    window.anomalyGate.record(result.hasAnomaly(), System.currentTimeMillis());
            if (decision.shouldAlert()) {
                handleAnomaly(player, result, decision.evidenceCount());
            }
        }
    }
    
    /**
     * Records a blocked packet for pattern analysis
     */
    public void recordBlockedPacket(Player player, String packetType) {
        if (player.hasPermission("lovelyspy.bypass") || !isTimingDetectionEnabled()) return;
        
        UUID uuid = player.getUniqueId();
        TimingWindow window = timingWindows.computeIfAbsent(uuid, k -> new TimingWindow());
        
        window.recordBlockedPacket(packetType);
        
        // Check if blocking pattern matches OpSec signature
        if (window.matchesOpSecBlockingPattern()
                && window.blockingAlertGate.tryAcquire(System.currentTimeMillis())) {
            executeTimingDetection(player, "opsec_blocking_pattern",
                    "Detected OpSec-style selective packet blocking pattern",
                    "Vector 6 (Timing Anomaly - Blocking Pattern)");
        }
    }
    
    /**
     * Compares timing between different packet types for consistency
     */
    public void analyzeTimingConsistency(Player player) {
        if (player.hasPermission("lovelyspy.bypass") || !isTimingDetectionEnabled()) return;
        
        UUID uuid = player.getUniqueId();
        TimingWindow window = timingWindows.get(uuid);
        if (window == null || window.getSampleCount() < WINDOW_SIZE) return;
        
        ConsistencyResult result = window.analyzeConsistency();
        if (!result.isConsistent()
                && window.consistencyAlertGate.tryAcquire(System.currentTimeMillis())) {
            executeTimingDetection(player, "timing_inconsistency",
                    "Packet timing inconsistency detected: " + result.getInconsistencyReason(),
                    "Vector 6 (Timing Anomaly - Consistency)");
        }
    }
    
    private void handleAnomaly(Player player, AnomalyResult result, int evidenceCount) {
        String evidence = String.format(
                "Timing anomaly burst: %d outliers within %dms; latest=%s "
                        + "(z-score: %.2f, expected: %.2fns, actual: %.2fns)",
                evidenceCount, ANOMALY_BURST_WINDOW_MS, result.getPacketType(),
                result.getZScore(), result.getExpectedValue(), result.getActualValue());
        
        executeTimingDetection(player, "timing_anomaly", evidence,
                "Vector 6 (Timing Anomaly Detection)");
    }

    private void executeTimingDetection(Player player, String evidenceKey,
                                        String evidence, String vectorName) {
        Config.ModEntry policy = timingPolicy();
        if (policy == null || !policy.enabled) {
            return;
        }
        plugin.executeModDetection(player, policy, Map.of(evidenceKey, evidence),
                vectorName, "Automatic");
    }

    private boolean isTimingDetectionEnabled() {
        Config.ModEntry policy = timingPolicy();
        return policy != null && policy.enabled;
    }

    private Config.ModEntry timingPolicy() {
        return plugin.getLovelyConfig().findPolicy(
                "opsec_timing_anomaly", "timing_anomaly");
    }
    
    public void cleanupPlayer(UUID uuid) {
        timingWindows.remove(uuid);
    }
    
    public void cleanup() {
        timingWindows.clear();
    }
    
    /**
     * Sliding window timing data structure with statistical analysis
     */
    private static class TimingWindow {
        private final LinkedList<Long> samples = new LinkedList<>();
        private final Map<String, Integer> blockedPacketCounts = new HashMap<>();
        private final Map<String, LinkedList<Long>> packetTypeSamples = new HashMap<>();
        private double ema = 0.0;
        private long sum = 0;
        private int count = 0;
        private final AlertGate anomalyGate = new AlertGate(
                ANOMALIES_REQUIRED, ANOMALY_BURST_WINDOW_MS, ALERT_COOLDOWN_MS);
        private final CooldownGate blockingAlertGate = new CooldownGate(ALERT_COOLDOWN_MS);
        private final CooldownGate consistencyAlertGate = new CooldownGate(ALERT_COOLDOWN_MS);
        
        public void addSample(String packetType, long durationNanos) {
            // Add to main window
            samples.addLast(durationNanos);
            sum += durationNanos;
            count++;
            
            // Maintain window size
            if (samples.size() > WINDOW_SIZE) {
                long removed = samples.removeFirst();
                sum -= removed;
                count--;
            }
            
            // Update EMA
            if (ema == 0.0) {
                ema = durationNanos;
            } else {
                ema = EMA_ALPHA * durationNanos + (1 - EMA_ALPHA) * ema;
            }
            
            // Add to packet-type specific window
            packetTypeSamples.computeIfAbsent(packetType, k -> new LinkedList<>()).addLast(durationNanos);
            LinkedList<Long> typeSamples = packetTypeSamples.get(packetType);
            if (typeSamples.size() > WINDOW_SIZE) {
                typeSamples.removeFirst();
            }
        }
        
        public void recordBlockedPacket(String packetType) {
            blockedPacketCounts.merge(packetType, 1, Integer::sum);
        }
        
        public AnomalyResult detectAnomalies() {
            if (samples.size() < WINDOW_SIZE) return new AnomalyResult(false, null, 0, 0, 0);
            
            double mean = (double) sum / count;
            double variance = computeVariance(mean);
            double stdDev = Math.sqrt(variance);
            
            // Check most recent sample for anomaly
            long mostRecent = samples.getLast();
            double zScore = stdDev > 0 ? (mostRecent - mean) / stdDev : 0;
            
            if (Math.abs(zScore) > Z_SCORE_THRESHOLD) {
                return new AnomalyResult(true, "recent_packet", zScore, mean, mostRecent);
            }
            
            // Check for EMA divergence
            double emaDivergence = Math.abs(ema - mean) / (stdDev > 0 ? stdDev : 1);
            if (emaDivergence > Z_SCORE_THRESHOLD) {
                return new AnomalyResult(true, "ema_divergence", emaDivergence, mean, ema);
            }
            
            return new AnomalyResult(false, null, 0, 0, 0);
        }
        
        public boolean matchesOpSecBlockingPattern() {
            // OpSec typically blocks specific packet types while allowing others
            // Check for selective blocking pattern
            int totalBlocked = blockedPacketCounts.values().stream().mapToInt(Integer::intValue).sum();
            if (totalBlocked < 3) return false;
            
            // Check if blocking is selective (not all or none)
            long uniquePacketTypes = packetTypeSamples.keySet().stream()
                    .filter(type -> packetTypeSamples.get(type).size() > 5)
                    .count();
            
            return totalBlocked > 0 && uniquePacketTypes > 1;
        }
        
        public ConsistencyResult analyzeConsistency() {
            // Analyze consistency between different packet types
            if (packetTypeSamples.size() < 2) {
                return new ConsistencyResult(true, "insufficient_data");
            }
            
            List<Double> means = new ArrayList<>();
            for (LinkedList<Long> typeSamples : packetTypeSamples.values()) {
                if (typeSamples.size() < 5) continue;
                double typeMean = typeSamples.stream().mapToLong(Long::longValue).average().orElse(0);
                means.add(typeMean);
            }
            
            if (means.size() < 2) {
                return new ConsistencyResult(true, "insufficient_sample_types");
            }
            
            // Check coefficient of variation
            double meanOfMeans = means.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double varianceOfMeans = computeVarianceOfList(means, meanOfMeans);
            double cv = meanOfMeans > 0 ? Math.sqrt(varianceOfMeans) / meanOfMeans : 0;
            
            // High CV suggests inconsistent timing (possible filtering)
            if (cv > 0.5) {
                return new ConsistencyResult(false, "high_coefficient_of_variation: " + cv);
            }
            
            return new ConsistencyResult(true, "consistent");
        }
        
        private double computeVariance(double mean) {
            double variance = 0.0;
            for (Long sample : samples) {
                variance += Math.pow(sample - mean, 2);
            }
            return variance / count;
        }
        
        private double computeVarianceOfList(List<Double> values, double mean) {
            double variance = 0.0;
            for (Double value : values) {
                variance += Math.pow(value - mean, 2);
            }
            return variance / values.size();
        }
        
        public int getSampleCount() {
            return count;
        }
    }

    static final class AlertGate {
        private final int evidenceRequired;
        private final long evidenceWindowMs;
        private final long cooldownMs;
        private final Deque<Long> evidenceTimes = new ArrayDeque<>();
        private long lastAlertAt = Long.MIN_VALUE;

        AlertGate(int evidenceRequired, long evidenceWindowMs, long cooldownMs) {
            this.evidenceRequired = Math.max(1, evidenceRequired);
            this.evidenceWindowMs = Math.max(1L, evidenceWindowMs);
            this.cooldownMs = Math.max(0L, cooldownMs);
        }

        synchronized Decision record(boolean anomalous, long now) {
            prune(now);
            if (!anomalous) {
                return new Decision(false, evidenceTimes.size());
            }
            evidenceTimes.addLast(now);
            int evidenceCount = evidenceTimes.size();
            boolean cooldownElapsed = lastAlertAt == Long.MIN_VALUE
                    || now - lastAlertAt >= cooldownMs;
            if (evidenceCount < evidenceRequired || !cooldownElapsed) {
                return new Decision(false, evidenceCount);
            }
            lastAlertAt = now;
            evidenceTimes.clear();
            return new Decision(true, evidenceCount);
        }

        private void prune(long now) {
            long oldestAllowed = now - evidenceWindowMs;
            while (!evidenceTimes.isEmpty() && evidenceTimes.peekFirst() < oldestAllowed) {
                evidenceTimes.removeFirst();
            }
        }

        record Decision(boolean shouldAlert, int evidenceCount) {
        }
    }

    private static final class CooldownGate {
        private final long cooldownMs;
        private long lastAlertAt = Long.MIN_VALUE;

        private CooldownGate(long cooldownMs) {
            this.cooldownMs = Math.max(0L, cooldownMs);
        }

        synchronized boolean tryAcquire(long now) {
            if (lastAlertAt != Long.MIN_VALUE && now - lastAlertAt < cooldownMs) {
                return false;
            }
            lastAlertAt = now;
            return true;
        }
    }
    
    private static class AnomalyResult {
        private final boolean hasAnomaly;
        private final String packetType;
        private final double zScore;
        private final double expectedValue;
        private final double actualValue;
        
        public AnomalyResult(boolean hasAnomaly, String packetType, double zScore, 
                           double expectedValue, double actualValue) {
            this.hasAnomaly = hasAnomaly;
            this.packetType = packetType;
            this.zScore = zScore;
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
        }
        
        public boolean hasAnomaly() { return hasAnomaly; }
        public String getPacketType() { return packetType; }
        public double getZScore() { return zScore; }
        public double getExpectedValue() { return expectedValue; }
        public double getActualValue() { return actualValue; }
    }
    
    private static class ConsistencyResult {
        private final boolean consistent;
        private final String inconsistencyReason;
        
        public ConsistencyResult(boolean consistent, String inconsistencyReason) {
            this.consistent = consistent;
            this.inconsistencyReason = inconsistencyReason;
        }
        
        public boolean isConsistent() { return consistent; }
        public String getInconsistencyReason() { return inconsistencyReason; }
    }
}
