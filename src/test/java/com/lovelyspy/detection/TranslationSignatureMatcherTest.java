package com.lovelyspy.detection;

import com.lovelyspy.util.ProbeComponentSerializer;

import java.util.List;

public final class TranslationSignatureMatcherTest {
    private static final List<String> METEOR_KEYS = List.of(
            "key.meteor-client.open-gui",
            "key.meteor-client.open-commands",
            "key.category.meteor-client.meteor-client"
    );

    private TranslationSignatureMatcherTest() {
    }

    public static void main(String[] args) {
        acceptsTwoConfirmedMeteorKeys();
        acceptsOneConfirmedMeteorKeyWithCurrentThreshold();
        rejectsNoMeteorKeys();
        duplicateConfiguredKeysDoNotIncreaseConfidence();
        meteorProbeComponentsFitExploitFixerLimit();
        packetArrivalTimestampExcludesSchedulerDelay();
        responseDurationNeverBecomesNegative();
        timingAnomaliesRequireABurst();
        timingAnomalyAlertsRespectCooldown();
        staleTimingAnomaliesExpire();
        baritoneBehaviorAloneIsInsufficient();
        grimFlagsAloneAreInsufficient();
        centeredTargetsAndGrimFlagsCorrelate();
        movementPatternsCanCorrelateWithGrim();
        expiredBaritoneEvidenceIsIgnored();
        baritoneDecisionCooldownSuppressesRepeats();
    }

    private static void acceptsTwoConfirmedMeteorKeys() {
        List<String> matches = TranslationSignatureMatcher.matchingKeys(METEOR_KEYS, List.of(
                "key.meteor-client.open-commands",
                "key.meteor-client.open-gui"
        ));

        assertEquals(List.of(
                "key.meteor-client.open-gui",
                "key.meteor-client.open-commands"
        ), matches);
        assertCondition(TranslationSignatureMatcher.meetsThreshold(matches, 2),
                "Two confirmed Meteor keys should meet the threshold");
    }

    private static void acceptsOneConfirmedMeteorKeyWithCurrentThreshold() {
        List<String> matches = TranslationSignatureMatcher.matchingKeys(
                METEOR_KEYS, List.of("key.meteor-client.open-gui"));

        assertCondition(TranslationSignatureMatcher.meetsThreshold(matches, 1),
                "One confirmed current Meteor key should meet the Meteor threshold after confirmation");
    }

    private static void rejectsNoMeteorKeys() {
        List<String> matches = TranslationSignatureMatcher.matchingKeys(METEOR_KEYS, List.of(
                "key.forward"
        ));

        assertCondition(!TranslationSignatureMatcher.meetsThreshold(matches, 1),
                "Unrelated keys must not meet the Meteor threshold");
    }

    private static void duplicateConfiguredKeysDoNotIncreaseConfidence() {
        List<String> matches = TranslationSignatureMatcher.matchingKeys(
                List.of("mod.key", "mod.key"), List.of("mod.key"));

        assertEquals(List.of("mod.key"), matches);
        assertCondition(!TranslationSignatureMatcher.meetsThreshold(matches, 2),
                "Duplicate configured keys must not satisfy a two-key threshold");
    }

    private static void meteorProbeComponentsFitExploitFixerLimit() {
        for (String key : METEOR_KEYS) {
            String component = ProbeComponentSerializer.serialize(key);
            assertCondition(component.length() <= ProbeComponentSerializer.SAFE_SIGN_COMPONENT_LENGTH,
                    "Probe component exceeds the safe sign limit: " + component);
            assertCondition(!component.contains("\"fallback\""),
                    "Probe components must not duplicate keys in a fallback field");
        }
        assertEquals(56, ProbeComponentSerializer.serialize(
                "key.category.meteor-client.meteor-client").length());
    }

    private static void packetArrivalTimestampExcludesSchedulerDelay() {
        long duration = Vector1_TranslationFingerprint.responseDuration(1_000L, 1_008L);
        assertEquals(8L, duration);
    }

    private static void responseDurationNeverBecomesNegative() {
        long duration = Vector1_TranslationFingerprint.responseDuration(1_001L, 1_000L);
        assertEquals(0L, duration);
    }

    private static void timingAnomaliesRequireABurst() {
        Vector6_TimingAnomalyDetection.AlertGate gate =
                new Vector6_TimingAnomalyDetection.AlertGate(3, 1_000L, 5_000L);

        assertCondition(!gate.record(true, 0L).shouldAlert(),
                "One timing outlier must not alert");
        assertCondition(!gate.record(true, 100L).shouldAlert(),
                "Two timing outliers must not alert");
        Vector6_TimingAnomalyDetection.AlertGate.Decision decision =
                gate.record(true, 200L);
        assertCondition(decision.shouldAlert(),
                "The configured timing-outlier burst should alert once");
        assertEquals(3, decision.evidenceCount());
    }

    private static void timingAnomalyAlertsRespectCooldown() {
        Vector6_TimingAnomalyDetection.AlertGate gate =
                new Vector6_TimingAnomalyDetection.AlertGate(2, 10_000L, 5_000L);

        gate.record(true, 0L);
        assertCondition(gate.record(true, 1L).shouldAlert(),
                "Initial timing burst should alert");
        gate.record(true, 100L);
        assertCondition(!gate.record(true, 101L).shouldAlert(),
                "Timing burst inside cooldown must not alert again");
        assertCondition(gate.record(true, 5_001L).shouldAlert(),
                "Accumulated evidence may alert after cooldown");
    }

    private static void staleTimingAnomaliesExpire() {
        Vector6_TimingAnomalyDetection.AlertGate gate =
                new Vector6_TimingAnomalyDetection.AlertGate(2, 100L, 0L);

        gate.record(true, 0L);
        assertCondition(!gate.record(true, 101L).shouldAlert(),
                "Expired timing evidence must not contribute to a burst");
        assertCondition(gate.record(true, 102L).shouldAlert(),
                "Fresh timing evidence inside the window should alert");
    }

    private static void baritoneBehaviorAloneIsInsufficient() {
        BaritoneEvidenceWindow evidence = new BaritoneEvidenceWindow();
        evidence.recordCenteredTarget("world:1:2:3", 1_000L);
        evidence.recordCenteredTarget("world:2:2:3", 1_001L);
        evidence.recordCenteredTarget("world:3:2:3", 1_002L);

        BaritoneEvidenceWindow.Decision decision =
                evidence.evaluate(1_100L, 1_000L, 3, 2, 2);
        assertCondition(!decision.meetsThreshold(),
                "Automation-like behavior without Grim evidence must not trigger");
    }

    private static void grimFlagsAloneAreInsufficient() {
        BaritoneEvidenceWindow evidence = new BaritoneEvidenceWindow();
        evidence.recordGrimFlag("Aim", 1_000L);
        evidence.recordGrimFlag("FastBreak", 1_001L);

        BaritoneEvidenceWindow.Decision decision =
                evidence.evaluate(1_100L, 1_000L, 3, 2, 2);
        assertCondition(!decision.meetsThreshold(),
                "Grim flags without LovelySpy behavior evidence must not trigger");
    }

    private static void centeredTargetsAndGrimFlagsCorrelate() {
        BaritoneEvidenceWindow evidence = new BaritoneEvidenceWindow();
        evidence.recordCenteredTarget("world:1:2:3", 1_000L);
        evidence.recordCenteredTarget("world:2:2:3", 1_001L);
        evidence.recordCenteredTarget("world:3:2:3", 1_002L);
        evidence.recordGrimFlag("Aim", 1_003L);
        evidence.recordGrimFlag("FastBreak", 1_004L);

        BaritoneEvidenceWindow.Decision decision =
                evidence.evaluate(1_100L, 1_000L, 3, 2, 2);
        assertCondition(decision.meetsThreshold(),
                "Centered targets plus independent Grim flags should correlate");
        assertEquals(3, decision.centeredTargets());
        assertEquals(2, decision.grimFlags());
    }

    private static void movementPatternsCanCorrelateWithGrim() {
        BaritoneEvidenceWindow evidence = new BaritoneEvidenceWindow();
        evidence.recordMovementPattern(1_000L);
        evidence.recordMovementPattern(1_001L);
        evidence.recordGrimFlag("Prediction", 1_002L);
        evidence.recordGrimFlag("Timer", 1_003L);

        BaritoneEvidenceWindow.Decision decision =
                evidence.evaluate(1_100L, 1_000L, 3, 2, 2);
        assertCondition(decision.meetsThreshold(),
                "Repeated movement patterns plus Grim flags should correlate");
    }

    private static void expiredBaritoneEvidenceIsIgnored() {
        BaritoneEvidenceWindow evidence = new BaritoneEvidenceWindow();
        evidence.recordCenteredTarget("world:1:2:3", 1_000L);
        evidence.recordCenteredTarget("world:2:2:3", 1_001L);
        evidence.recordCenteredTarget("world:3:2:3", 1_002L);
        evidence.recordGrimFlag("Aim", 1_003L);
        evidence.recordGrimFlag("FastBreak", 1_004L);

        BaritoneEvidenceWindow.Decision decision =
                evidence.evaluate(3_000L, 500L, 3, 2, 2);
        assertCondition(!decision.meetsThreshold(),
                "Evidence outside the correlation window must expire");
        assertEquals(0, decision.centeredTargets());
        assertEquals(0, decision.grimFlags());
    }

    private static void baritoneDecisionCooldownSuppressesRepeats() {
        BaritoneEvidenceWindow evidence = new BaritoneEvidenceWindow();
        evidence.recordMovementPattern(1_000L);
        evidence.recordMovementPattern(1_001L);
        evidence.recordGrimFlag("Prediction", 1_002L);
        evidence.recordGrimFlag("Timer", 1_003L);
        assertCondition(evidence.evaluate(1_100L, 1_000L, 3, 2, 2).meetsThreshold(),
                "Initial correlation should trigger");

        evidence.beginCooldown(1_100L, 5_000L);
        evidence.recordMovementPattern(1_200L);
        evidence.recordMovementPattern(1_201L);
        evidence.recordGrimFlag("Prediction", 1_202L);
        evidence.recordGrimFlag("Timer", 1_203L);
        assertCondition(!evidence.evaluate(1_300L, 1_000L, 3, 2, 2).meetsThreshold(),
                "Cooldown must suppress a repeated decision");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
