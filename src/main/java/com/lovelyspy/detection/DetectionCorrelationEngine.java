package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detection Correlation Engine
 * 
 * Multi-vector Bayesian correlation system that combines evidence from all
 * detection vectors to provide comprehensive OpSec detection with high confidence.
 * 
 * Algorithm: Bayesian Network with Evidence Aggregation
 * - Maintains prior probabilities for each detection vector
 * - Updates posterior probabilities using Bayes' theorem
 * - Combines evidence using belief propagation
 * - Computes final confidence score using ensemble methods
 */
public final class DetectionCorrelationEngine {
    
    // Bayesian network parameters
    private static final double OPSEC_PRIOR_PROBABILITY = 0.05; // 5% prior probability
    private static final double CONFIDENCE_THRESHOLD = 0.85;
    private static final double HIGH_RISK_THRESHOLD = 0.70;
    private static final double MEDIUM_RISK_THRESHOLD = 0.50;
    
    // Vector reliability weights (based on false positive rates)
    private static final Map<String, Double> VECTOR_RELIABILITY = Map.of(
            "Vector1_TranslationFingerprint", 0.85,
            "Vector2_BrandChannelAnalysis", 0.75,
            "Vector3_PrivacyModDetection", 0.90,
            "Vector4_ResourcePackAltDetection", 0.80,
            "Vector5_BaritoneBehaviorDetection", 0.70,
            "Vector6_TimingAnomalyDetection", 0.75,
            "Vector7_BehavioralConsistencyDetection", 0.85,
            "Vector8_AdvancedResourcePackDetection", 0.80
    );
    
    // Conditional probabilities: P(Vector | OpSec)
    private static final Map<String, Double> OPSEC_VECTOR_CONDITIONAL = Map.of(
            "Vector1_TranslationFingerprint", 0.15, // OpSec blocks translations
            "Vector2_BrandChannelAnalysis", 0.90, // OpSec spoofs brand
            "Vector3_PrivacyModDetection", 0.95, // OpSec's primary purpose
            "Vector4_ResourcePackAltDetection", 0.85, // OpSec isolates cache
            "Vector5_BaritoneBehaviorDetection", 0.20, // Not directly related
            "Vector6_TimingAnomalyDetection", 0.70, // OpSec causes timing anomalies
            "Vector7_BehavioralConsistencyDetection", 0.80, // OpSec creates inconsistencies
            "Vector8_AdvancedResourcePackDetection", 0.85 // OpSec manipulates packs
    );
    
    // Conditional probabilities: P(Vector | No OpSec)
    private static final Map<String, Double> CLEAN_VECTOR_CONDITIONAL = Map.of(
            "Vector1_TranslationFingerprint", 0.10, // Some mods trigger this
            "Vector2_BrandChannelAnalysis", 0.15, // Some modded clients
            "Vector3_PrivacyModDetection", 0.05, // Rare for clean clients
            "Vector4_ResourcePackAltDetection", 0.10, // Network issues
            "Vector5_BaritoneBehaviorDetection", 0.08, // Legitimate automation
            "Vector6_TimingAnomalyDetection", 0.15, // Network lag
            "Vector7_BehavioralConsistencyDetection", 0.10, // Inconsistent play
            "Vector8_AdvancedResourcePackDetection", 0.12 // Fast connections
    );
    
    private final LovelySpyPlugin plugin;
    private final Map<UUID, PlayerDetectionProfile> profiles = new ConcurrentHashMap<>();
    
    public DetectionCorrelationEngine(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Records a detection from any vector
     */
    public void recordDetection(Player player, String vectorName, String detectionType, 
                               String evidence, double confidence) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        PlayerDetectionProfile profile = profiles.computeIfAbsent(uuid, k -> new PlayerDetectionProfile());
        
        profile.addDetection(vectorName, detectionType, evidence, confidence);
        
        // Update posterior probability
        updatePosteriorProbability(profile);
        
        // Check if threshold met
        if (profile.getPosteriorProbability() >= CONFIDENCE_THRESHOLD) {
            handleHighConfidenceDetection(player, profile);
        } else if (profile.getPosteriorProbability() >= HIGH_RISK_THRESHOLD) {
            handleHighRiskDetection(player, profile);
        } else if (profile.getPosteriorProbability() >= MEDIUM_RISK_THRESHOLD) {
            handleMediumRiskDetection(player, profile);
        }
    }
    
    /**
     * Updates posterior probability using Bayes' theorem
     */
    private void updatePosteriorProbability(PlayerDetectionProfile profile) {
        double posterior = OPSEC_PRIOR_PROBABILITY;
        
        // Apply Bayesian updates for each detection
        for (DetectionRecord record : profile.getDetections()) {
            String vectorName = record.vectorName;
            
            double pVectorGivenOpSec = OPSEC_VECTOR_CONDITIONAL.getOrDefault(vectorName, 0.5);
            double pVectorGivenClean = CLEAN_VECTOR_CONDITIONAL.getOrDefault(vectorName, 0.5);
            
            // Bayes' theorem: P(OpSec | Vector) = P(Vector | OpSec) * P(OpSec) / P(Vector)
            double pVector = (pVectorGivenOpSec * posterior) + (pVectorGivenClean * (1 - posterior));
            posterior = (pVectorGivenOpSec * posterior) / pVector;
            
            // Weight by vector reliability
            double reliability = VECTOR_RELIABILITY.getOrDefault(vectorName, 0.5);
            posterior = 0.5 + (posterior - 0.5) * reliability;
        }
        
        // Apply temporal decay (recent detections weighted more heavily)
        posterior = applyTemporalDecay(profile, posterior);
        
        // Apply correlation boost (if multiple vectors agree)
        posterior = applyCorrelationBoost(profile, posterior);
        
        profile.setPosteriorProbability(Math.min(0.99, Math.max(0.01, posterior)));
    }
    
    /**
     * Applies temporal decay to posterior probability
     */
    private double applyTemporalDecay(PlayerDetectionProfile profile, double posterior) {
        long currentTime = System.currentTimeMillis();
        long decayWindow = 5 * 60 * 1000; // 5 minutes
        
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        
        for (DetectionRecord record : profile.getDetections()) {
            long age = currentTime - record.timestamp;
            double weight = Math.exp(-age / decayWindow);
            
            weightedSum += record.confidence * weight;
            totalWeight += weight;
        }
        
        if (totalWeight > 0) {
            double weightedConfidence = weightedSum / totalWeight;
            return 0.5 + (posterior - 0.5) * weightedConfidence;
        }
        
        return posterior;
    }
    
    /**
     * Applies correlation boost when multiple vectors agree
     */
    private double applyCorrelationBoost(PlayerDetectionProfile profile, double posterior) {
        Map<String, Integer> vectorCounts = new HashMap<>();
        for (DetectionRecord record : profile.getDetections()) {
            vectorCounts.merge(record.vectorName, 1, Integer::sum);
        }
        
        // If multiple different vectors triggered, boost confidence
        int uniqueVectors = vectorCounts.size();
        if (uniqueVectors >= 3) {
            return Math.min(0.99, posterior * 1.2);
        } else if (uniqueVectors == 2) {
            return Math.min(0.99, posterior * 1.1);
        }
        
        return posterior;
    }
    
    private void handleHighConfidenceDetection(Player player, PlayerDetectionProfile profile) {
        String evidence = buildCorrelationEvidence(profile);
        
        plugin.executeDetection(player, "opsec_correlation_high_confidence",
                evidence,
                "Correlation Engine (High Confidence: " + 
                        String.format("%.1f%%", profile.getPosteriorProbability() * 100) + ")",
                "Automatic");
    }
    
    private void handleHighRiskDetection(Player player, PlayerDetectionProfile profile) {
        String evidence = buildCorrelationEvidence(profile);
        
        plugin.executeDetection(player, "opsec_correlation_high_risk",
                evidence,
                "Correlation Engine (High Risk: " + 
                        String.format("%.1f%%", profile.getPosteriorProbability() * 100) + ")",
                "Automatic");
    }
    
    private void handleMediumRiskDetection(Player player, PlayerDetectionProfile profile) {
        String evidence = buildCorrelationEvidence(profile);
        
        plugin.executeDetection(player, "opsec_correlation_medium_risk",
                evidence,
                "Correlation Engine (Medium Risk: " + 
                        String.format("%.1f%%", profile.getPosteriorProbability() * 100) + ")",
                "Automatic");
    }
    
    private String buildCorrelationEvidence(PlayerDetectionProfile profile) {
        StringBuilder evidence = new StringBuilder();
        evidence.append("Multi-vector correlation: ");
        
        Map<String, Integer> vectorCounts = new HashMap<>();
        for (DetectionRecord record : profile.getDetections()) {
            vectorCounts.merge(record.vectorName, 1, Integer::sum);
        }
        
        List<String> vectorSummary = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : vectorCounts.entrySet()) {
            vectorSummary.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        
        evidence.append(String.join(", ", vectorSummary));
        evidence.append(String.format(" | Confidence: %.1f%%", profile.getPosteriorProbability() * 100));
        
        return evidence.toString();
    }
    
    /**
     * Gets the current posterior probability for a player
     */
    public double getPosteriorProbability(UUID uuid) {
        PlayerDetectionProfile profile = profiles.get(uuid);
        return profile != null ? profile.getPosteriorProbability() : OPSEC_PRIOR_PROBABILITY;
    }
    
    /**
     * Gets detailed correlation analysis for a player
     */
    public CorrelationAnalysis getCorrelationAnalysis(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerDetectionProfile profile = profiles.get(uuid);
        if (profile == null) {
            return new CorrelationAnalysis(OPSEC_PRIOR_PROBABILITY, List.of(), Map.of());
        }
        
        Map<String, Integer> vectorCounts = new HashMap<>();
        for (DetectionRecord record : profile.getDetections()) {
            vectorCounts.merge(record.vectorName, 1, Integer::sum);
        }
        
        return new CorrelationAnalysis(
                profile.getPosteriorProbability(),
                new ArrayList<>(profile.getDetections()),
                vectorCounts
        );
    }
    
    public void cleanupPlayer(UUID uuid) {
        profiles.remove(uuid);
    }
    
    public void cleanup() {
        profiles.clear();
    }
    
    /**
     * Player detection profile with Bayesian tracking
     */
    private static class PlayerDetectionProfile {
        private final List<DetectionRecord> detections = new ArrayList<>();
        private double posteriorProbability = OPSEC_PRIOR_PROBABILITY;
        
        public void addDetection(String vectorName, String detectionType, 
                                String evidence, double confidence) {
            DetectionRecord record = new DetectionRecord(
                    vectorName, detectionType, evidence, confidence, System.currentTimeMillis()
            );
            detections.add(record);
            
            // Keep only recent detections (last 20)
            if (detections.size() > 20) {
                detections.remove(0);
            }
        }
        
        public List<DetectionRecord> getDetections() {
            return new ArrayList<>(detections);
        }
        
        public double getPosteriorProbability() {
            return posteriorProbability;
        }
        
        public void setPosteriorProbability(double posteriorProbability) {
            this.posteriorProbability = posteriorProbability;
        }
    }
    
    private static class DetectionRecord {
        final String vectorName;
        final String detectionType;
        final String evidence;
        final double confidence;
        final long timestamp;
        
        DetectionRecord(String vectorName, String detectionType, String evidence, 
                       double confidence, long timestamp) {
            this.vectorName = vectorName;
            this.detectionType = detectionType;
            this.evidence = evidence;
            this.confidence = confidence;
            this.timestamp = timestamp;
        }
    }
    
    public static class CorrelationAnalysis {
        private final double posteriorProbability;
        private final List<DetectionRecord> detections;
        private final Map<String, Integer> vectorCounts;
        
        CorrelationAnalysis(double posteriorProbability, List<DetectionRecord> detections, 
                          Map<String, Integer> vectorCounts) {
            this.posteriorProbability = posteriorProbability;
            this.detections = detections;
            this.vectorCounts = vectorCounts;
        }
        
        public double getPosteriorProbability() { return posteriorProbability; }
        public List<DetectionRecord> getDetections() { return detections; }
        public Map<String, Integer> getVectorCounts() { return vectorCounts; }
    }
}
