package com.lovelyspy.detection;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks per-packet-type timing distributions and emits persistent bimodality.
 *
 * <p>Packet types are deliberately kept separate. Pooling unrelated packet
 * handlers produces a multimodal distribution even on a clean client.</p>
 */
final class TimingBimodalityDetector {
    private final int minimumSamples;
    private final int requiredConsecutiveWindows;
    private final int evaluationStride;
    private final long minimumObservationNanos;
    private final Map<String, TimingSeries> seriesByPacketType = new HashMap<>();

    TimingBimodalityDetector(int minimumSamples, int requiredConsecutiveWindows) {
        this(minimumSamples, requiredConsecutiveWindows, 0L);
    }

    TimingBimodalityDetector(int minimumSamples, int requiredConsecutiveWindows,
                             long minimumObservationNanos) {
        if (minimumSamples < 4) {
            throw new IllegalArgumentException("minimumSamples must be at least 4");
        }
        if (requiredConsecutiveWindows < 1) {
            throw new IllegalArgumentException(
                    "requiredConsecutiveWindows must be positive");
        }
        if (minimumObservationNanos < 0L) {
            throw new IllegalArgumentException(
                    "minimumObservationNanos cannot be negative");
        }
        this.minimumSamples = minimumSamples;
        this.requiredConsecutiveWindows = requiredConsecutiveWindows;
        this.evaluationStride = Math.max(1, minimumSamples / 4);
        this.minimumObservationNanos = minimumObservationNanos;
    }

    synchronized Optional<Result> addSample(String packetType, long durationNanos,
                                            double threshold) {
        if (packetType == null || packetType.isBlank()
                || !Double.isFinite(threshold)) {
            return Optional.empty();
        }

        TimingSeries series = seriesByPacketType.computeIfAbsent(
                packetType, ignored -> new TimingSeries());
        series.samples.addLast(Math.max(0L, durationNanos));
        while (series.samples.size() > minimumSamples) {
            series.samples.removeFirst();
        }
        series.samplesSinceEvaluation++;
        series.observedNanos = saturatingAdd(
                series.observedNanos, Math.max(0L, durationNanos));

        if (series.reported || series.samples.size() < minimumSamples
                || series.observedNanos < minimumObservationNanos
                || series.samplesSinceEvaluation < evaluationStride) {
            return Optional.empty();
        }
        series.samplesSinceEvaluation = 0;

        double coefficient = computeCoefficient(series.samples);
        if (coefficient > threshold) {
            series.consecutiveBimodalWindows++;
        } else {
            series.consecutiveBimodalWindows = 0;
        }

        if (series.consecutiveBimodalWindows < requiredConsecutiveWindows) {
            return Optional.empty();
        }
        series.reported = true;
        return Optional.of(new Result(
                packetType, coefficient, series.samples.size(),
                series.consecutiveBimodalWindows));
    }

    /**
     * Computes Sarle's population bimodality coefficient.
     *
     * <p>Using raw kurtosis, the denominator is {@code kurtosis}, not
     * {@code kurtosis + 3}. The latter would add three twice and is the formula
     * inconsistency present in the research pseudocode.</p>
     */
    static double computeCoefficient(Collection<? extends Number> samples) {
        if (samples == null || samples.size() < 4) {
            return 0.0;
        }

        double mean = 0.0;
        for (Number sample : samples) {
            mean += sample.doubleValue();
        }
        mean /= samples.size();

        double secondMoment = 0.0;
        double thirdMoment = 0.0;
        double fourthMoment = 0.0;
        for (Number sample : samples) {
            double difference = sample.doubleValue() - mean;
            double squared = difference * difference;
            secondMoment += squared;
            thirdMoment += squared * difference;
            fourthMoment += squared * squared;
        }

        double count = samples.size();
        secondMoment /= count;
        thirdMoment /= count;
        fourthMoment /= count;
        if (secondMoment <= 1.0e-12 || fourthMoment <= 1.0e-12) {
            return 0.0;
        }

        double skewness = thirdMoment / Math.pow(secondMoment, 1.5);
        double rawKurtosis = fourthMoment / (secondMoment * secondMoment);
        if (!Double.isFinite(skewness)
                || !Double.isFinite(rawKurtosis)
                || rawKurtosis <= 0.0) {
            return 0.0;
        }
        double coefficient =
                (skewness * skewness + 1.0) / rawKurtosis;
        return Double.isFinite(coefficient) ? coefficient : 0.0;
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    record Result(String packetType, double coefficient, int sampleCount,
                  int consecutiveWindows) {
    }

    private static final class TimingSeries {
        private final Deque<Long> samples = new ArrayDeque<>();
        private int samplesSinceEvaluation;
        private int consecutiveBimodalWindows;
        private long observedNanos;
        private boolean reported;
    }
}
