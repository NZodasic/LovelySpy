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
