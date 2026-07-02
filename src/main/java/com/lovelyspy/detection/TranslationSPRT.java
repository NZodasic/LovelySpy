package com.lovelyspy.detection;

/**
 * Wald sequential probability-ratio test for translation probe observations.
 *
 * <p>The likelihoods come from section 3.1.2 of the mathematical design:
 * a resolved response can be quick or slow, while an unresolved response is
 * treated as the timeout/blocked category regardless of packet arrival time.
 * The detector only produces evidence; the caller remains responsible for
 * applying LovelySpy's independent confirmation policy.</p>
 */
final class TranslationSPRT {
    enum Decision {
        H0_ACCEPT,
        H1_ACCEPT,
        CONTINUE
    }

    private static final double QUICK_H0 = 0.90;
    private static final double QUICK_H1 = 0.05;
    private static final double SLOW_H0 = 0.08;
    private static final double SLOW_H1 = 0.15;
    private static final double BLOCKED_H0 = 0.02;
    private static final double BLOCKED_H1 = 0.80;

    private final double logLower;
    private final double logUpper;
    private final int maxSamples;
    private final long fastThresholdMs;
    private final long slowThresholdMs;

    private double logLikelihoodRatio;
    private int sampleCount;
    private Decision decision = Decision.CONTINUE;

    TranslationSPRT(double alpha, double beta, int maxSamples,
                    long fastThresholdMs, long slowThresholdMs) {
        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be between 0 and 1");
        }
        if (!(beta > 0.0 && beta < 1.0)) {
            throw new IllegalArgumentException("beta must be between 0 and 1");
        }
        if (maxSamples < 1) {
            throw new IllegalArgumentException("maxSamples must be positive");
        }
        if (fastThresholdMs < 0 || slowThresholdMs <= fastThresholdMs) {
            throw new IllegalArgumentException(
                    "slowThresholdMs must be greater than fastThresholdMs");
        }
        this.logLower = Math.log(beta / (1.0 - alpha));
        this.logUpper = Math.log((1.0 - beta) / alpha);
        this.maxSamples = maxSamples;
        this.fastThresholdMs = fastThresholdMs;
        this.slowThresholdMs = slowThresholdMs;
    }

    synchronized Decision update(boolean resolved, long responseTimeMs) {
        if (decision != Decision.CONTINUE) {
            return decision;
        }

        sampleCount++;
        logLikelihoodRatio += logLikelihoodRatio(resolved,
                Math.max(0L, responseTimeMs));

        if (logLikelihoodRatio >= logUpper) {
            decision = Decision.H1_ACCEPT;
        } else if (logLikelihoodRatio <= logLower) {
            decision = Decision.H0_ACCEPT;
        } else if (sampleCount >= maxSamples) {
            decision = logLikelihoodRatio > 0.0
                    ? Decision.H1_ACCEPT
                    : Decision.H0_ACCEPT;
        }
        return decision;
    }

    private double logLikelihoodRatio(boolean resolved, long responseTimeMs) {
        if (!resolved || responseTimeMs > slowThresholdMs) {
            return Math.log(BLOCKED_H1 / BLOCKED_H0);
        }
        if (responseTimeMs < fastThresholdMs) {
            return Math.log(QUICK_H1 / QUICK_H0);
        }
        return Math.log(SLOW_H1 / SLOW_H0);
    }

    synchronized boolean isTerminal() {
        return decision != Decision.CONTINUE;
    }

    synchronized double getLogLikelihoodRatio() {
        return logLikelihoodRatio;
    }

    synchronized int getSampleCount() {
        return sampleCount;
    }

    synchronized void reset() {
        logLikelihoodRatio = 0.0;
        sampleCount = 0;
        decision = Decision.CONTINUE;
    }
}
