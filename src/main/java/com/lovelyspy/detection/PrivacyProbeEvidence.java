package com.lovelyspy.detection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record PrivacyProbeEvidence(Set<String> unresolvedKeys, Map<String, String> responses) {
    public PrivacyProbeEvidence {
        unresolvedKeys = unresolvedKeys == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(unresolvedKeys));
        responses = responses == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(responses));
    }

    public static PrivacyProbeEvidence empty() {
        return new PrivacyProbeEvidence(Set.of(), Map.of());
    }

    public boolean hasUnresolved() {
        return !unresolvedKeys.isEmpty();
    }

    public PrivacyProbeEvidence merge(PrivacyProbeEvidence other) {
        if (other == null || (!other.hasUnresolved() && other.responses.isEmpty())) {
            return this;
        }

        LinkedHashSet<String> mergedUnresolved = new LinkedHashSet<>(unresolvedKeys);
        mergedUnresolved.addAll(other.unresolvedKeys);

        LinkedHashMap<String, String> mergedResponses = new LinkedHashMap<>(responses);
        mergedResponses.putAll(other.responses);

        return new PrivacyProbeEvidence(mergedUnresolved, mergedResponses);
    }
}
