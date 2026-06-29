package com.lovelyspy.detection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TranslationSignatureMatcher {
    private TranslationSignatureMatcher() {
    }

    public static List<String> matchingKeys(List<String> configuredKeys,
                                            Collection<String> confirmedKeys) {
        Set<String> confirmed = new LinkedHashSet<>(confirmedKeys);
        return configuredKeys.stream()
                .filter(confirmed::contains)
                .distinct()
                .toList();
    }

    public static boolean meetsThreshold(List<String> matchingKeys, int minimumMatches) {
        return matchingKeys.size() >= Math.max(1, minimumMatches);
    }
}
