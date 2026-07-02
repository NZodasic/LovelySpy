package com.lovelyspy.detection;

/**
 * EWMA latency baseline with the Chebyshev lower bound from section 3.1.4.
 */
final class AdaptiveLatencyThreshold {
    private final double alpha;
    private final double chebyshevK;
    private final int minimumSamples;
    private final long minimumThresholdMs;

    private double ewmaMean;
    private double ewmaVariance;
    private int sampleCount;

    AdaptiveLatencyThreshold(double alpha, double chebyshevK,
                             int minimumSamples, long minimumThresholdMs) {
        if (!(alpha > 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("EWMA alpha must be in (0, 1]");
        }
        if (!(chebyshevK > 0.0)) {
            throw new IllegalArgumentException("Chebyshev k must be positive");
        }
        if (minimumSamples < 1) {
            throw new IllegalArgumentException("minimumSamples must be positive");
        }
        if (minimumThresholdMs < 0L) {
            throw new IllegalArgumentException("minimumThresholdMs cannot be negative");
        }
        this.alpha = alpha;
        this.chebyshevK = chebyshevK;
        this.minimumSamples = minimumSamples;
        this.minimumThresholdMs = minimumThresholdMs;
    }

    synchronized void update(long latencyMs) {
        double sample = Math.max(0L, latencyMs);
        if (sampleCount == 0) {
            ewmaMean = sample;
            ewmaVariance = 0.0;
            sampleCount = 1;
            return;
        }

        double oldMean = ewmaMean;
        ewmaMean = alpha * sample + (1.0 - alpha) * oldMean;
        ewmaVariance = (1.0 - alpha) * ewmaVariance
                + alpha * (sample - ewmaMean) * (sample - oldMean);
        // Floating-point cancellation can create a tiny negative value.
        ewmaVariance = Math.max(0.0, ewmaVariance);
        sampleCount++;
    }

    /**
     * Returns a conservative fast-response boundary.
     *
     * <p>The learned boundary is capped by the prior fixed boundary. Adaptation
     * can therefore reduce false positives on jittery connections but cannot
     * make the existing interception rule more aggressive.</p>
     */
    synchronized long getThreshold(long fallbackThresholdMs) {
        long fallback = Math.max(minimumThresholdMs, fallbackThresholdMs);
        if (sampleCount < minimumSamples) {
            return fallback;
        }
        double lowerBound = ewmaMean
                - chebyshevK * Math.sqrt(ewmaVariance);
        long adaptive = (long) Math.floor(
                Math.max(minimumThresholdMs, lowerBound));
        return Math.min(fallback, adaptive);
    }

    synchronized double getZScore(long latencyMs) {
        double stdDev = Math.sqrt(ewmaVariance);
        if (sampleCount < 2 || stdDev < 1.0e-9) {
            return 0.0;
        }
        return (Math.max(0L, latencyMs) - ewmaMean) / stdDev;
    }

    synchronized int getSampleCount() {
        return sampleCount;
    }
}
