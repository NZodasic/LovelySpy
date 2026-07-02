package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vector 8: Advanced Resource Pack Fingerprinting
 * 
 * Uses time series analysis and pattern recognition to detect OpSec's
 * cache isolation and resource pack spoofing mechanisms.
 * 
 * Algorithm: Time Series Analysis with Pattern Recognition
 * - Maintains time series of resource pack events
 * - Detects anomalies using statistical process control
 * - Identifies cache isolation through timing patterns
 * - Uses machine learning-style pattern matching
 */
public final class Vector8_AdvancedResourcePackDetection {
    
    // Detection thresholds
    private static final int TIME_SERIES_WINDOW = 20;
    private static final double ANOMALY_THRESHOLD = 2.5;
    private static final long CACHE_ISOLATION_THRESHOLD_MS = 50;
    private static final int MIN_PATTERN_MATCHES = 3;
    
    private final LovelySpyPlugin plugin;
    private final Map<UUID, ResourcePackTimeSeries> timeSeriesMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> resourcePackSignatures = new ConcurrentHashMap<>();
    
    public Vector8_AdvancedResourcePackDetection(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Records resource pack send event
     */
    public void recordPackSend(Player player, String packUrl, String packHash) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        ResourcePackTimeSeries series = timeSeriesMap.computeIfAbsent(uuid, k -> new ResourcePackTimeSeries());
        
        series.recordEvent("SEND", Map.of(
                "url", packUrl,
                "hash", packHash,
                "timestamp", System.currentTimeMillis()
        ));
        
        // Analyze for patterns
        if (series.getEventCount() >= TIME_SERIES_WINDOW) {
            analyzeTimeSeries(player, series);
        }
    }
    
    /**
     * Records resource pack accept event
     */
    public void recordPackAccept(Player player, long durationMs) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        ResourcePackTimeSeries series = timeSeriesMap.computeIfAbsent(uuid, k -> new ResourcePackTimeSeries());
        
        series.recordEvent("ACCEPT", Map.of(
                "duration", durationMs,
                "timestamp", System.currentTimeMillis()
        ));
        
        // Check for instant accept (cache isolation indicator)
        if (durationMs < CACHE_ISOLATION_THRESHOLD_MS) {
            checkCacheIsolationPattern(player, series, durationMs);
        }
        
        // Analyze for patterns
        if (series.getEventCount() >= TIME_SERIES_WINDOW) {
            analyzeTimeSeries(player, series);
        }
    }
    
    /**
     * Records resource pack load event
     */
    public void recordPackLoad(Player player, long durationMs, boolean success) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        ResourcePackTimeSeries series = timeSeriesMap.computeIfAbsent(uuid, k -> new ResourcePackTimeSeries());
        
        series.recordEvent("LOAD", Map.of(
                "duration", durationMs,
                "success", success,
                "timestamp", System.currentTimeMillis()
        ));
        
        // Analyze for patterns
        if (series.getEventCount() >= TIME_SERIES_WINDOW) {
            analyzeTimeSeries(player, series);
        }
    }
    
    /**
     * Records resource pack decline/error event
     */
    public void recordPackDecline(Player player, String reason) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        ResourcePackTimeSeries series = timeSeriesMap.computeIfAbsent(uuid, k -> new ResourcePackTimeSeries());
        
        series.recordEvent("DECLINE", Map.of(
                "reason", reason,
                "timestamp", System.currentTimeMillis()
        ));
        
        // Analyze for patterns
        if (series.getEventCount() >= TIME_SERIES_WINDOW) {
            analyzeTimeSeries(player, series);
        }
    }
    
    /**
     * Analyzes time series for anomalies
     */
    private void analyzeTimeSeries(Player player, ResourcePackTimeSeries series) {
        TimeSeriesAnalysisResult result = series.analyze();
        
        if (result.hasAnomaly()) {
            handleAnomaly(player, result);
        }
        
        // Check for OpSec-specific patterns
        OpSecPatternResult opsecResult = series.detectOpSecPatterns();
        if (opsecResult.isOpSecDetected()) {
            handleOpSecPattern(player, opsecResult);
        }
    }
    
    /**
     * Checks for cache isolation pattern
     */
    private void checkCacheIsolationPattern(Player player, ResourcePackTimeSeries series, long durationMs) {
        // Cache isolation shows as consistently fast accepts across different sessions
        List<Long> recentAcceptDurations = series.getRecentDurations("ACCEPT", 5);
        
        if (recentAcceptDurations.size() >= 3) {
            long avgDuration = recentAcceptDurations.stream().mapToLong(Long::longValue).sum() / recentAcceptDurations.size();
            
            // If most accepts are instant, likely cache isolation
            if (avgDuration < CACHE_ISOLATION_THRESHOLD_MS * 2) {
                plugin.executeDetection(player, "cache_isolation_pattern",
                        "Detected cache isolation pattern (avg accept: " + avgDuration + "ms)",
                        "Vector 8 (Resource Pack - Cache Isolation)", "Automatic");
            }
        }
    }
    
    private void handleAnomaly(Player player, TimeSeriesAnalysisResult result) {
        String evidence = String.format("Resource pack timing anomaly: %s (z-score: %.2f)",
                result.getAnomalyType(), result.getZScore());
        
        plugin.executeDetection(player, "resource_pack_timing_anomaly",
                evidence,
                "Vector 8 (Resource Pack Timing Anomaly)", "Automatic");
    }
    
    private void handleOpSecPattern(Player player, OpSecPatternResult result) {
        String evidence = "OpSec resource pack pattern detected: " + result.getPatternDescription();
        
        plugin.executeDetection(player, "opsec_resource_pack_pattern",
                evidence,
                "Vector 8 (OpSec Resource Pack Pattern)", "Automatic");
    }
    
    /**
     * Performs cross-session resource pack analysis
     */
    public void analyzeCrossSessionPatterns(Player player) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        ResourcePackTimeSeries series = timeSeriesMap.get(uuid);
        if (series == null || series.getEventCount() < TIME_SERIES_WINDOW) return;
        
        CrossSessionResult result = series.analyzeCrossSessionPatterns();
        if (result.hasSuspiciousPattern()) {
            plugin.executeDetection(player, "cross_session_pack_pattern",
                    "Suspicious cross-session resource pack pattern: " + result.getPatternType(),
                    "Vector 8 (Cross-Session Pattern)", "Automatic");
        }
    }
    
    public void cleanupPlayer(UUID uuid) {
        timeSeriesMap.remove(uuid);
        resourcePackSignatures.remove(uuid);
    }
    
    public void cleanup() {
        timeSeriesMap.clear();
        resourcePackSignatures.clear();
    }
    
    /**
     * Time series analysis data structure for resource pack events
     */
    private static class ResourcePackTimeSeries {
        private final List<ResourcePackEvent> events = new ArrayList<>();
        private final Map<String, List<Long>> eventTypeDurations = new HashMap<>();
        
        public void recordEvent(String eventType, Map<String, Object> attributes) {
            ResourcePackEvent event = new ResourcePackEvent(eventType, attributes);
            events.add(event);
            
            // Extract duration if present
            if (attributes.containsKey("duration")) {
                Object durationObj = attributes.get("duration");
                if (durationObj instanceof Number) {
                    long duration = ((Number) durationObj).longValue();
                    eventTypeDurations.computeIfAbsent(eventType, k -> new ArrayList<>()).add(duration);
                }
            }
            
            // Maintain window size
            if (events.size() > TIME_SERIES_WINDOW * 2) {
                events.remove(0);
            }
        }
        
        public TimeSeriesAnalysisResult analyze() {
            if (events.size() < TIME_SERIES_WINDOW) {
                return new TimeSeriesAnalysisResult(false, null, 0, "insufficient_data");
            }
            
            // Analyze timing patterns
            List<Long> timestamps = new ArrayList<>();
            for (ResourcePackEvent event : events) {
                timestamps.add((Long) event.attributes.get("timestamp"));
            }
            
            // Compute intervals
            List<Long> intervals = new ArrayList<>();
            for (int i = 1; i < timestamps.size(); i++) {
                intervals.add(timestamps.get(i) - timestamps.get(i - 1));
            }
            
            // Statistical analysis
            double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
            double variance = intervals.stream()
                    .mapToDouble(interval -> Math.pow(interval - mean, 2))
                    .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            
            // Check for anomalies using z-score
            for (int i = 0; i < intervals.size(); i++) {
                double zScore = stdDev > 0 ? (intervals.get(i) - mean) / stdDev : 0;
                if (Math.abs(zScore) > ANOMALY_THRESHOLD) {
                    return new TimeSeriesAnalysisResult(true, "interval_anomaly", zScore, 
                            "interval_" + i);
                }
            }
            
            // Analyze duration patterns by event type
            for (Map.Entry<String, List<Long>> entry : eventTypeDurations.entrySet()) {
                List<Long> durations = entry.getValue();
                if (durations.size() < 5) continue;
                
                double durationMean = durations.stream().mapToLong(Long::longValue).average().orElse(0);
                double durationVariance = durations.stream()
                        .mapToDouble(d -> Math.pow(d - durationMean, 2))
                        .average().orElse(0);
                double durationStdDev = Math.sqrt(durationVariance);
                
                // Check for duration anomalies
                for (Long duration : durations) {
                    double zScore = durationStdDev > 0 ? (duration - durationMean) / durationStdDev : 0;
                    if (Math.abs(zScore) > ANOMALY_THRESHOLD) {
                        return new TimeSeriesAnalysisResult(true, 
                                "duration_anomaly_" + entry.getKey(), zScore, 
                                "event_type_" + entry.getKey());
                    }
                }
            }
            
            return new TimeSeriesAnalysisResult(false, null, 0, "normal");
        }
        
        public OpSecPatternResult detectOpSecPatterns() {
            // Pattern 1: Consistently instant accepts (cache isolation)
            List<Long> acceptDurations = getRecentDurations("ACCEPT", 10);
            if (acceptDurations.size() >= MIN_PATTERN_MATCHES) {
                long instantAccepts = acceptDurations.stream()
                        .filter(d -> d < CACHE_ISOLATION_THRESHOLD_MS)
                        .count();
                
                if (instantAccepts >= acceptDurations.size() * 0.7) {
                    return new OpSecPatternResult(true, "cache_isolation",
                            "Consistently instant accepts (" + instantAccepts + "/" + acceptDurations.size() + ")");
                }
            }
            
            // Pattern 2: Skipped decline then accept (bypass pattern)
            boolean hasDeclineThenAccept = checkDeclineThenAcceptPattern();
            if (hasDeclineThenAccept) {
                return new OpSecPatternResult(true, "decline_then_accept_bypass",
                        "Decline then immediate accept pattern detected");
            }
            
            // Pattern 3: Inconsistent load times despite fast accepts
            List<Long> acceptDurationsList = getRecentDurations("ACCEPT", 5);
            List<Long> loadDurations = getRecentDurations("LOAD", 5);
            
            if (acceptDurationsList.size() >= 3 && loadDurations.size() >= 3) {
                long avgAccept = acceptDurationsList.stream().mapToLong(Long::longValue).sum() / acceptDurationsList.size();
                long avgLoad = loadDurations.stream().mapToLong(Long::longValue).sum() / loadDurations.size();
                
                // Fast accepts but slow loads suggests spoofing
                if (avgAccept < CACHE_ISOLATION_THRESHOLD_MS * 2 && avgLoad > 1000) {
                    return new OpSecPatternResult(true, "accept_load_mismatch",
                            "Fast accepts (" + avgAccept + "ms) but slow loads (" + avgLoad + "ms)");
                }
            }
            
            return new OpSecPatternResult(false, "none", "No OpSec patterns detected");
        }
        
        private boolean checkDeclineThenAcceptPattern() {
            for (int i = 0; i < events.size() - 1; i++) {
                ResourcePackEvent current = events.get(i);
                ResourcePackEvent next = events.get(i + 1);
                
                if (current.eventType.equals("DECLINE") && next.eventType.equals("ACCEPT")) {
                    long currentTimestamp = (Long) current.attributes.get("timestamp");
                    long nextTimestamp = (Long) next.attributes.get("timestamp");
                    
                    // If accept happens very quickly after decline, suspicious
                    if (nextTimestamp - currentTimestamp < 1000) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        public CrossSessionResult analyzeCrossSessionPatterns() {
            // Check for consistent patterns across multiple resource pack interactions
            if (events.size() < TIME_SERIES_WINDOW) {
                return new CrossSessionResult(false, "insufficient_data");
            }
            
            // Analyze hash consistency
            Set<String> hashes = new HashSet<>();
            for (ResourcePackEvent event : events) {
                if (event.attributes.containsKey("hash")) {
                    hashes.add((String) event.attributes.get("hash"));
                }
            }
            
            // If many different hashes but consistent timing, suspicious
            if (hashes.size() > 3) {
                List<Long> acceptDurations = getRecentDurations("ACCEPT", 10);
                if (acceptDurations.size() >= 5) {
                    double cv = computeCoefficientOfVariation(acceptDurations);
                    if (cv < 0.3) {
                        return new CrossSessionResult(true, "consistent_timing_across_different_packs");
                    }
                }
            }
            
            return new CrossSessionResult(false, "normal");
        }
        
        private double computeCoefficientOfVariation(List<Long> values) {
            if (values.isEmpty()) return 0;
            
            double mean = values.stream().mapToLong(Long::longValue).average().orElse(0);
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            
            return mean > 0 ? stdDev / mean : 0;
        }
        
        public List<Long> getRecentDurations(String eventType, int count) {
            List<Long> durations = eventTypeDurations.getOrDefault(eventType, List.of());
            int start = Math.max(0, durations.size() - count);
            return new ArrayList<>(durations.subList(start, durations.size()));
        }
        
        public int getEventCount() {
            return events.size();
        }
    }
    
    private static class ResourcePackEvent {
        final String eventType;
        final Map<String, Object> attributes;
        
        ResourcePackEvent(String eventType, Map<String, Object> attributes) {
            this.eventType = eventType;
            this.attributes = new HashMap<>(attributes);
        }
    }
    
    private static class TimeSeriesAnalysisResult {
        private final boolean hasAnomaly;
        private final String anomalyType;
        private final double zScore;
        private final String description;
        
        TimeSeriesAnalysisResult(boolean hasAnomaly, String anomalyType, double zScore, String description) {
            this.hasAnomaly = hasAnomaly;
            this.anomalyType = anomalyType;
            this.zScore = zScore;
            this.description = description;
        }
        
        public boolean hasAnomaly() { return hasAnomaly; }
        public String getAnomalyType() { return anomalyType; }
        public double getZScore() { return zScore; }
    }
    
    private static class OpSecPatternResult {
        private final boolean opSecDetected;
        private final String patternType;
        private final String patternDescription;
        
        OpSecPatternResult(boolean opSecDetected, String patternType, String patternDescription) {
            this.opSecDetected = opSecDetected;
            this.patternType = patternType;
            this.patternDescription = patternDescription;
        }
        
        public boolean isOpSecDetected() { return opSecDetected; }
        public String getPatternType() { return patternType; }
        public String getPatternDescription() { return patternDescription; }
    }
    
    private static class CrossSessionResult {
        private final boolean hasSuspiciousPattern;
        private final String patternType;
        
        CrossSessionResult(boolean hasSuspiciousPattern, String patternType) {
            this.hasSuspiciousPattern = hasSuspiciousPattern;
            this.patternType = patternType;
        }
        
        public boolean hasSuspiciousPattern() { return hasSuspiciousPattern; }
        public String getPatternType() { return patternType; }
    }
}
