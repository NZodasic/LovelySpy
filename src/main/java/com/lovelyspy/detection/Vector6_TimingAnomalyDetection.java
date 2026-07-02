package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
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
    
    private final LovelySpyPlugin plugin;
    private final Map<UUID, TimingWindow> timingWindows = new ConcurrentHashMap<>();
    
    public Vector6_TimingAnomalyDetection(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Records a packet timing sample for anomaly detection
     */
    public void recordPacketTiming(Player player, String packetType, long durationNanos) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        TimingWindow window = timingWindows.computeIfAbsent(uuid, k -> new TimingWindow());
        
        window.addSample(packetType, durationNanos);
        
        // Check for anomalies after sufficient samples
        if (window.getSampleCount() >= WINDOW_SIZE) {
            AnomalyResult result = window.detectAnomalies();
            if (result.hasAnomaly()) {
                handleAnomaly(player, result);
            }
        }
    }
    
    /**
     * Records a blocked packet for pattern analysis
     */
    public void recordBlockedPacket(Player player, String packetType) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        TimingWindow window = timingWindows.computeIfAbsent(uuid, k -> new TimingWindow());
        
        window.recordBlockedPacket(packetType);
        
        // Check if blocking pattern matches OpSec signature
        if (window.matchesOpSecBlockingPattern()) {
            plugin.executeDetection(player, "opsec_blocking_pattern",
                    "Detected OpSec-style selective packet blocking pattern",
                    "Vector 6 (Timing Anomaly - Blocking Pattern)", "Automatic");
        }
    }
    
    /**
     * Compares timing between different packet types for consistency
     */
    public void analyzeTimingConsistency(Player player) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        TimingWindow window = timingWindows.get(uuid);
        if (window == null || window.getSampleCount() < WINDOW_SIZE) return;
        
        ConsistencyResult result = window.analyzeConsistency();
        if (!result.isConsistent()) {
            plugin.executeDetection(player, "timing_inconsistency",
                    "Packet timing inconsistency detected: " + result.getInconsistencyReason(),
                    "Vector 6 (Timing Anomaly - Consistency)", "Automatic");
        }
    }
    
    private void handleAnomaly(Player player, AnomalyResult result) {
        String evidence = String.format("Timing anomaly: %s (z-score: %.2f, expected: %.2fns, actual: %.2fns)",
                result.getPacketType(), result.getZScore(), result.getExpectedValue(), result.getActualValue());
        
        plugin.executeDetection(player, "timing_anomaly",
                evidence,
                "Vector 6 (Timing Anomaly Detection)", "Automatic");
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
