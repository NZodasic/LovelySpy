package com.lovelyspy.detection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Time-bounded correlation state for Baritone-like automation evidence.
 *
 * <p>No individual movement or anti-cheat flag identifies Baritone. A decision
 * requires both a repeated automation pattern and independent Grim evidence.</p>
 */
public final class BaritoneEvidenceWindow {
    private final Deque<CenteredTarget> centeredTargets = new ArrayDeque<>();
    private final Deque<Long> movementPatterns = new ArrayDeque<>();
    private final Deque<GrimFlag> grimFlags = new ArrayDeque<>();
    private long cooldownUntil;

    public synchronized void recordCenteredTarget(String blockKey, long now) {
        if (blockKey == null || blockKey.isBlank()) return;
        boolean duplicate = centeredTargets.stream()
                .anyMatch(target -> target.blockKey.equals(blockKey));
        if (!duplicate) {
            centeredTargets.addLast(new CenteredTarget(blockKey, now));
        }
    }

    public synchronized void recordMovementPattern(long now) {
        movementPatterns.addLast(now);
    }

    public synchronized void recordGrimFlag(String checkName, long now) {
        if (checkName == null || checkName.isBlank()) return;
        grimFlags.addLast(new GrimFlag(checkName, now));
    }

    public synchronized Decision evaluate(long now, long windowMillis,
                                          int minimumCenteredTargets,
                                          int minimumMovementPatterns,
                                          int minimumGrimFlags) {
        prune(now, windowMillis);
        Set<String> grimChecks = new LinkedHashSet<>();
        for (GrimFlag flag : grimFlags) {
            grimChecks.add(flag.checkName);
        }

        boolean automationPattern = centeredTargets.size() >= Math.max(1, minimumCenteredTargets)
                || movementPatterns.size() >= Math.max(1, minimumMovementPatterns);
        boolean corroborated = grimFlags.size() >= Math.max(1, minimumGrimFlags);
        return new Decision(
                now >= cooldownUntil && automationPattern && corroborated,
                centeredTargets.size(),
                movementPatterns.size(),
                grimFlags.size(),
                Set.copyOf(grimChecks));
    }

    public synchronized void beginCooldown(long now, long cooldownMillis) {
        cooldownUntil = now + Math.max(0L, cooldownMillis);
        centeredTargets.clear();
        movementPatterns.clear();
        grimFlags.clear();
    }

    private void prune(long now, long windowMillis) {
        long oldest = now - Math.max(1L, windowMillis);
        while (!centeredTargets.isEmpty() && centeredTargets.peekFirst().timestamp < oldest) {
            centeredTargets.removeFirst();
        }
        while (!movementPatterns.isEmpty() && movementPatterns.peekFirst() < oldest) {
            movementPatterns.removeFirst();
        }
        while (!grimFlags.isEmpty() && grimFlags.peekFirst().timestamp < oldest) {
            grimFlags.removeFirst();
        }
    }

    public record Decision(boolean meetsThreshold,
                           int centeredTargets,
                           int movementPatterns,
                           int grimFlags,
                           Set<String> grimChecks) {
    }

    private record CenteredTarget(String blockKey, long timestamp) {
    }

    private record GrimFlag(String checkName, long timestamp) {
    }
}
