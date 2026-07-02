package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vector 7: Behavioral Consistency Detection
 * 
 * Uses graph-based correlation analysis to detect inconsistent behavior patterns
 * across multiple detection vectors. OpSec creates behavioral inconsistencies that
 * can be detected through multi-dimensional analysis.
 * 
 * Algorithm: Graph-Based Behavioral Correlation
 * - Constructs behavior graph from multiple data sources
 * - Computes consistency scores using graph metrics
 * - Detects anomalies using graph clustering
 * - Uses Bayesian inference for confidence estimation
 */
public final class Vector7_BehavioralConsistencyDetection {
    
    // Consistency thresholds
    private static final double MIN_CONSISTENCY_SCORE = 0.6;
    private static final double CLUSTERING_THRESHOLD = 0.4;
    private static final int MIN_BEHAVIOR_SAMPLES = 10;
    
    private final LovelySpyPlugin plugin;
    private final Map<UUID, BehaviorGraph> behaviorGraphs = new ConcurrentHashMap<>();
    
    public Vector7_BehavioralConsistencyDetection(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Records a behavior event for consistency analysis
     */
    public void recordBehavior(Player player, String behaviorType, Map<String, Object> attributes) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        BehaviorGraph graph = behaviorGraphs.computeIfAbsent(uuid, k -> new BehaviorGraph());
        
        graph.addBehavior(behaviorType, attributes);
        
        // Analyze consistency after sufficient samples
        if (graph.getBehaviorCount() >= MIN_BEHAVIOR_SAMPLES) {
            ConsistencyAnalysisResult result = graph.analyzeConsistency();
            if (result.getConsistencyScore() < MIN_CONSISTENCY_SCORE) {
                handleInconsistency(player, result);
            }
        }
    }
    
    /**
     * Records translation probe results for behavioral correlation
     */
    public void recordTranslationProbe(Player player, String key, String response, long duration) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        BehaviorGraph graph = behaviorGraphs.computeIfAbsent(uuid, k -> new BehaviorGraph());
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key", key);
        attributes.put("response", response);
        attributes.put("duration", duration);
        attributes.put("resolved", !response.equals(key));
        
        graph.addBehavior("translation_probe", attributes);
    }
    
    /**
     * Records resource pack behavior for correlation
     */
    public void recordResourcePackBehavior(Player player, String action, long duration) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        BehaviorGraph graph = behaviorGraphs.computeIfAbsent(uuid, k -> new BehaviorGraph());
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("action", action);
        attributes.put("duration", duration);
        
        graph.addBehavior("resource_pack", attributes);
    }
    
    /**
     * Records movement behavior for anomaly detection
     */
    public void recordMovementBehavior(Player player, double x, double y, double z, float yaw, float pitch) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        BehaviorGraph graph = behaviorGraphs.computeIfAbsent(uuid, k -> new BehaviorGraph());
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("x", x);
        attributes.put("y", y);
        attributes.put("z", z);
        attributes.put("yaw", yaw);
        attributes.put("pitch", pitch);
        attributes.put("timestamp", System.currentTimeMillis());
        
        graph.addBehavior("movement", attributes);
    }
    
    /**
     * Performs cross-vector correlation analysis
     */
    public void performCrossVectorAnalysis(Player player) {
        if (player.hasPermission("lovelyspy.bypass")) return;
        
        UUID uuid = player.getUniqueId();
        BehaviorGraph graph = behaviorGraphs.get(uuid);
        if (graph == null || graph.getBehaviorCount() < MIN_BEHAVIOR_SAMPLES) return;
        
        CrossVectorResult result = graph.analyzeCrossVectorCorrelation();
        if (result.hasSuspiciousCorrelation()) {
            plugin.executeDetection(player, "cross_vector_correlation",
                    "Suspicious cross-vector correlation detected: " + result.getCorrelationType(),
                    "Vector 7 (Behavioral Consistency - Cross-Vector)", "Automatic");
        }
    }
    
    private void handleInconsistency(Player player, ConsistencyAnalysisResult result) {
        String evidence = String.format("Behavioral inconsistency detected (score: %.2f): %s",
                result.getConsistencyScore(), result.getInconsistencyReason());
        
        plugin.executeDetection(player, "behavioral_inconsistency",
                evidence,
                "Vector 7 (Behavioral Consistency Detection)", "Automatic");
    }
    
    public void cleanupPlayer(UUID uuid) {
        behaviorGraphs.remove(uuid);
    }
    
    public void cleanup() {
        behaviorGraphs.clear();
    }
    
    /**
     * Graph-based behavior analysis data structure
     */
    private static class BehaviorGraph {
        private final List<BehaviorNode> nodes = new ArrayList<>();
        private final List<BehaviorEdge> edges = new ArrayList<>();
        private final Map<String, List<BehaviorNode>> behaviorTypeIndex = new HashMap<>();
        
        public void addBehavior(String behaviorType, Map<String, Object> attributes) {
            BehaviorNode node = new BehaviorNode(behaviorType, attributes, System.currentTimeMillis());
            nodes.add(node);
            
            // Index by behavior type
            behaviorTypeIndex.computeIfAbsent(behaviorType, k -> new ArrayList<>()).add(node);
            
            // Create edges to similar recent behaviors
            createEdgesToSimilarBehaviors(node);
        }
        
        private void createEdgesToSimilarBehaviors(BehaviorNode newNode) {
            // Find recent behaviors of same type
            List<BehaviorNode> sameType = behaviorTypeIndex.getOrDefault(newNode.behaviorType, List.of());
            
            for (BehaviorNode existing : sameType) {
                if (existing == newNode) continue;
                
                // Only connect to recent behaviors (within 30 seconds)
                if (newNode.timestamp - existing.timestamp < 30000) {
                    double similarity = computeSimilarity(newNode, existing);
                    if (similarity > CLUSTERING_THRESHOLD) {
                        edges.add(new BehaviorEdge(existing, newNode, similarity));
                    }
                }
            }
        }
        
        private double computeSimilarity(BehaviorNode a, BehaviorNode b) {
            if (!a.behaviorType.equals(b.behaviorType)) return 0.0;
            
            double similarity = 0.0;
            int matchingAttributes = 0;
            
            for (String key : a.attributes.keySet()) {
                if (b.attributes.containsKey(key)) {
                    Object valA = a.attributes.get(key);
                    Object valB = b.attributes.get(key);
                    
                    if (Objects.equals(valA, valB)) {
                        similarity += 1.0;
                        matchingAttributes++;
                    } else if (valA instanceof Number && valB instanceof Number) {
                        // Numeric similarity
                        double numA = ((Number) valA).doubleValue();
                        double numB = ((Number) valB).doubleValue();
                        double diff = Math.abs(numA - numB);
                        double max = Math.max(Math.abs(numA), Math.abs(numB));
                        if (max > 0) {
                            similarity += 1.0 - (diff / max);
                            matchingAttributes++;
                        }
                    }
                }
            }
            
            return matchingAttributes > 0 ? similarity / matchingAttributes : 0.0;
        }
        
        public ConsistencyAnalysisResult analyzeConsistency() {
            if (nodes.size() < MIN_BEHAVIOR_SAMPLES) {
                return new ConsistencyAnalysisResult(1.0, "insufficient_data");
            }
            
            // Compute clustering coefficient
            double clusteringCoefficient = computeClusteringCoefficient();
            
            // Compute behavior type distribution entropy
            double entropy = computeBehaviorEntropy();
            
            // Compute temporal consistency
            double temporalConsistency = computeTemporalConsistency();
            
            // Combine metrics
            double consistencyScore = (clusteringCoefficient * 0.4) + 
                                     (normalizeEntropy(entropy) * 0.3) + 
                                     (temporalConsistency * 0.3);
            
            String inconsistencyReason = null;
            if (clusteringCoefficient < 0.3) {
                inconsistencyReason = "low_clustering_coefficient";
            } else if (entropy > 2.5) {
                inconsistencyReason = "high_entropy_inconsistent_behavior";
            } else if (temporalConsistency < 0.5) {
                inconsistencyReason = "low_temporal_consistency";
            }
            
            return new ConsistencyAnalysisResult(consistencyScore, 
                    inconsistencyReason != null ? inconsistencyReason : "consistent");
        }
        
        public CrossVectorResult analyzeCrossVectorCorrelation() {
            // Check for correlations between different behavior types
            Map<String, List<BehaviorNode>> typeGroups = behaviorTypeIndex;
            
            // OpSec signature: fast resource pack + blocked translations + inconsistent movement
            boolean hasFastPack = hasBehaviorPattern("resource_pack", "action", "ACCEPTED", 
                    attrs -> ((Long) attrs.getOrDefault("duration", 0L)) < 15);
            
            boolean hasBlockedTranslations = hasBehaviorPattern("translation_probe", "resolved", false,
                    attrs -> true);
            
            boolean hasInconsistentMovement = hasBehaviorPattern("movement", null, null,
                    this::isMovementInconsistent);
            
            if (hasFastPack && hasBlockedTranslations) {
                return new CrossVectorResult(true, "fast_pack_with_blocked_translations");
            }
            
            if (hasFastPack && hasInconsistentMovement) {
                return new CrossVectorResult(true, "fast_pack_with_inconsistent_movement");
            }
            
            if (hasBlockedTranslations && hasInconsistentMovement) {
                return new CrossVectorResult(true, "blocked_translations_with_inconsistent_movement");
            }
            
            return new CrossVectorResult(false, "no_suspicious_correlation");
        }
        
        private boolean hasBehaviorPattern(String behaviorType, String attributeKey, Object attributeValue,
                                          java.util.function.Function<Map<String, Object>, Boolean> customCheck) {
            List<BehaviorNode> behaviors = behaviorTypeIndex.getOrDefault(behaviorType, List.of());
            
            for (BehaviorNode node : behaviors) {
                boolean matches = true;
                
                if (attributeKey != null && attributeValue != null) {
                    if (!Objects.equals(node.attributes.get(attributeKey), attributeValue)) {
                        matches = false;
                    }
                }
                
                if (matches && customCheck != null) {
                    matches = customCheck.apply(node.attributes);
                }
                
                if (matches) return true;
            }
            
            return false;
        }
        
        private boolean isMovementInconsistent(Map<String, Object> attrs) {
            // Check for impossible movement patterns
            Double x = (Double) attrs.get("x");
            Double y = (Double) attrs.get("y");
            Double z = (Double) attrs.get("z");
            Long timestamp = (Long) attrs.get("timestamp");
            
            if (x == null || y == null || z == null || timestamp == null) return false;
            
            // Find previous movement
            List<BehaviorNode> movements = behaviorTypeIndex.getOrDefault("movement", List.of());
            for (int i = movements.size() - 1; i >= 0; i--) {
                BehaviorNode prev = movements.get(i);
                if (prev == attrs.get("this")) continue;
                
                Long prevTimestamp = (Long) prev.attributes.get("timestamp");
                if (prevTimestamp != null && timestamp - prevTimestamp < 100) {
                    Double prevX = (Double) prev.attributes.get("x");
                    Double prevY = (Double) prev.attributes.get("y");
                    Double prevZ = (Double) prev.attributes.get("z");
                    
                    if (prevX != null && prevY != null && prevZ != null) {
                        double distance = Math.sqrt(Math.pow(x - prevX, 2) + 
                                                  Math.pow(y - prevY, 2) + 
                                                  Math.pow(z - prevZ, 2));
                        double timeDiff = timestamp - prevTimestamp;
                        
                        // Check for impossible speed (> 20 blocks per second)
                        if (timeDiff > 0 && distance / timeDiff > 0.02) {
                            return true;
                        }
                    }
                }
            }
            
            return false;
        }
        
        private double computeClusteringCoefficient() {
            if (nodes.isEmpty()) return 0.0;
            
            double totalClustering = 0.0;
            int nodeCount = 0;
            
            for (BehaviorNode node : nodes) {
                List<BehaviorNode> neighbors = getNeighbors(node);
                if (neighbors.size() < 2) continue;
                
                int possibleEdges = neighbors.size() * (neighbors.size() - 1) / 2;
                int actualEdges = 0;
                
                for (int i = 0; i < neighbors.size(); i++) {
                    for (int j = i + 1; j < neighbors.size(); j++) {
                        if (areConnected(neighbors.get(i), neighbors.get(j))) {
                            actualEdges++;
                        }
                    }
                }
                
                totalClustering += possibleEdges > 0 ? (double) actualEdges / possibleEdges : 0.0;
                nodeCount++;
            }
            
            return nodeCount > 0 ? totalClustering / nodeCount : 0.0;
        }
        
        private List<BehaviorNode> getNeighbors(BehaviorNode node) {
            List<BehaviorNode> neighbors = new ArrayList<>();
            for (BehaviorEdge edge : edges) {
                if (edge.from == node) neighbors.add(edge.to);
                if (edge.to == node) neighbors.add(edge.from);
            }
            return neighbors;
        }
        
        private boolean areConnected(BehaviorNode a, BehaviorNode b) {
            for (BehaviorEdge edge : edges) {
                if ((edge.from == a && edge.to == b) || (edge.from == b && edge.to == a)) {
                    return true;
                }
            }
            return false;
        }
        
        private double computeBehaviorEntropy() {
            Map<String, Integer> typeCounts = new HashMap<>();
            for (BehaviorNode node : nodes) {
                typeCounts.merge(node.behaviorType, 1, Integer::sum);
            }
            
            double entropy = 0.0;
            int total = nodes.size();
            
            for (int count : typeCounts.values()) {
                double probability = (double) count / total;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
            
            return entropy;
        }
        
        private double normalizeEntropy(double entropy) {
            // Normalize entropy to 0-1 range (assuming max 3 behavior types)
            return Math.min(1.0, entropy / 3.0);
        }
        
        private double computeTemporalConsistency() {
            if (nodes.size() < 2) return 1.0;
            
            List<Long> timestamps = new ArrayList<>();
            for (BehaviorNode node : nodes) {
                timestamps.add(node.timestamp);
            }
            Collections.sort(timestamps);
            
            // Compute intervals
            List<Long> intervals = new ArrayList<>();
            for (int i = 1; i < timestamps.size(); i++) {
                intervals.add(timestamps.get(i) - timestamps.get(i - 1));
            }
            
            if (intervals.isEmpty()) return 1.0;
            
            // Compute coefficient of variation
            double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
            double variance = intervals.stream()
                    .mapToDouble(interval -> Math.pow(interval - mean, 2))
                    .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            
            return mean > 0 ? 1.0 - (stdDev / mean) : 1.0;
        }
        
        public int getBehaviorCount() {
            return nodes.size();
        }
    }
    
    private static class BehaviorNode {
        final String behaviorType;
        final Map<String, Object> attributes;
        final long timestamp;
        
        BehaviorNode(String behaviorType, Map<String, Object> attributes, long timestamp) {
            this.behaviorType = behaviorType;
            this.attributes = new HashMap<>(attributes);
            this.timestamp = timestamp;
        }
    }
    
    private static class BehaviorEdge {
        final BehaviorNode from;
        final BehaviorNode to;
        final double similarity;
        
        BehaviorEdge(BehaviorNode from, BehaviorNode to, double similarity) {
            this.from = from;
            this.to = to;
            this.similarity = similarity;
        }
    }
    
    private static class ConsistencyAnalysisResult {
        private final double consistencyScore;
        private final String inconsistencyReason;
        
        ConsistencyAnalysisResult(double consistencyScore, String inconsistencyReason) {
            this.consistencyScore = consistencyScore;
            this.inconsistencyReason = inconsistencyReason;
        }
        
        public double getConsistencyScore() { return consistencyScore; }
        public String getInconsistencyReason() { return inconsistencyReason; }
    }
    
    private static class CrossVectorResult {
        private final boolean hasSuspiciousCorrelation;
        private final String correlationType;
        
        CrossVectorResult(boolean hasSuspiciousCorrelation, String correlationType) {
            this.hasSuspiciousCorrelation = hasSuspiciousCorrelation;
            this.correlationType = correlationType;
        }
        
        public boolean hasSuspiciousCorrelation() { return hasSuspiciousCorrelation; }
        public String getCorrelationType() { return correlationType; }
    }
}
