package com.lovelyspy.detection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PrivacyProbeState {
    private final Deque<String> pendingKeys;
    private final Set<String> unresolvedKeys;
    private final Map<String, String> responses;

    public PrivacyProbeState(Collection<String> pendingKeys) {
        this(pendingKeys, Set.of(), Map.of());
    }

    public PrivacyProbeState(Collection<String> pendingKeys,
                             Collection<String> unresolvedKeys,
                             Map<String, String> responses) {
        this.pendingKeys = new ArrayDeque<>(normalizeUnique(pendingKeys));
        this.unresolvedKeys = new LinkedHashSet<>(normalizeUnique(unresolvedKeys));
        this.responses = responses != null
                ? new LinkedHashMap<>(responses)
                : new LinkedHashMap<>();
    }

    public List<String> takeBatch(int maxKeys) {
        List<String> batch = new ArrayList<>(Math.max(0, maxKeys));
        for (int i = 0; i < maxKeys && !pendingKeys.isEmpty(); i++) {
            batch.add(pendingKeys.removeFirst());
        }
        return batch;
    }

    public boolean hasPending() {
        return !pendingKeys.isEmpty();
    }

    public void record(PrivacyProbeEvidence evidence) {
        if (evidence == null) return;
        unresolvedKeys.addAll(evidence.unresolvedKeys());
        responses.putAll(evidence.responses());
    }

    public Set<String> repeatedUnresolvedKeys(Collection<String> confirmedUnresolved) {
        LinkedHashSet<String> repeated = new LinkedHashSet<>(unresolvedKeys);
        repeated.retainAll(new LinkedHashSet<>(normalizeUnique(confirmedUnresolved)));
        return repeated;
    }

    public Set<String> unresolvedKeys() {
        return Collections.unmodifiableSet(unresolvedKeys);
    }

    public Map<String, String> responses() {
        return Collections.unmodifiableMap(responses);
    }

    private static List<String> normalizeUnique(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null) continue;
            String cleaned = key.trim();
            if (!cleaned.isEmpty()) {
                unique.add(cleaned);
            }
        }
        return new ArrayList<>(unique);
    }
}
