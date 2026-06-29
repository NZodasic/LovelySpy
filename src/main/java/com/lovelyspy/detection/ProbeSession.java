package com.lovelyspy.detection;

import com.lovelyspy.util.SchedulerHelper;
import org.bukkit.Location;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProbeSession {
    private final UUID uuid;
    private final String name;
    private final Location location;
    private final List<String> testedKeys;
    private final List<String> allPendingKeys;
    private final List<String> flaggedKeys;
    private final Map<String, String> responses;
    private final boolean privacyProbe;
    private final List<String> controlKeys;
    private final List<String> pendingControlKeys;
    private final List<String> unresolvedControlKeys;
    private final Map<String, String> controlResponses;
    private final String checker;
    private final long startTime;
    private final boolean isConfirmation;
    
    private SchedulerHelper.LovelyTask timeoutTask;

    public ProbeSession(UUID uuid, String name, Location location, List<String> testedKeys, 
                        List<String> allPendingKeys, List<String> flaggedKeys, boolean isConfirmation) {
        this(uuid, name, location, testedKeys, allPendingKeys, flaggedKeys, isConfirmation, "Automatic");
    }

    public ProbeSession(UUID uuid, String name, Location location, List<String> testedKeys,
                        List<String> allPendingKeys, List<String> flaggedKeys, boolean isConfirmation,
                        String checker) {
        this(uuid, name, location, testedKeys, allPendingKeys, flaggedKeys, isConfirmation,
                checker, null);
    }

    public ProbeSession(UUID uuid, String name, Location location, List<String> testedKeys,
                        List<String> allPendingKeys, List<String> flaggedKeys, boolean isConfirmation,
                        String checker, Map<String, String> responses) {
        this(uuid, name, location, testedKeys, allPendingKeys, flaggedKeys, isConfirmation,
                checker, responses, false, List.of(), List.of(), List.of(), Map.of());
    }

    public ProbeSession(UUID uuid, String name, Location location, List<String> testedKeys,
                        List<String> allPendingKeys, List<String> flaggedKeys, boolean isConfirmation,
                        String checker, Map<String, String> responses, boolean privacyProbe,
                        List<String> controlKeys, List<String> pendingControlKeys,
                        List<String> unresolvedControlKeys, Map<String, String> controlResponses) {
        this.uuid = uuid;
        this.name = name;
        this.location = location;
        this.testedKeys = testedKeys;
        this.allPendingKeys = allPendingKeys != null ? allPendingKeys : new ArrayList<>();
        this.flaggedKeys = flaggedKeys != null ? flaggedKeys : new ArrayList<>();
        this.responses = responses != null ? new LinkedHashMap<>(responses) : new LinkedHashMap<>();
        this.privacyProbe = privacyProbe;
        this.controlKeys = controlKeys != null ? new ArrayList<>(controlKeys) : new ArrayList<>();
        this.pendingControlKeys = pendingControlKeys != null ? new ArrayList<>(pendingControlKeys) : new ArrayList<>();
        this.unresolvedControlKeys = unresolvedControlKeys != null ? new ArrayList<>(unresolvedControlKeys) : new ArrayList<>();
        this.controlResponses = controlResponses != null ? new LinkedHashMap<>(controlResponses) : new LinkedHashMap<>();
        this.checker = checker == null || checker.isBlank() ? "Automatic" : checker;
        this.startTime = System.currentTimeMillis();
        this.isConfirmation = isConfirmation;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public List<String> getTestedKeys() {
        return testedKeys;
    }

    public List<String> getAllPendingKeys() {
        return allPendingKeys;
    }

    public List<String> getFlaggedKeys() {
        return flaggedKeys;
    }

    public Map<String, String> getResponses() {
        return responses;
    }

    public void addResponses(Map<String, String> pageResponses) {
        responses.putAll(pageResponses);
    }

    public boolean isPrivacyProbe() {
        return privacyProbe;
    }

    public List<String> getControlKeys() {
        return controlKeys;
    }

    public List<String> getPendingControlKeys() {
        return pendingControlKeys;
    }

    public List<String> getUnresolvedControlKeys() {
        return unresolvedControlKeys;
    }

    public Map<String, String> getControlResponses() {
        return controlResponses;
    }

    public String getChecker() {
        return checker;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isConfirmation() {
        return isConfirmation;
    }

    public SchedulerHelper.LovelyTask getTimeoutTask() {
        return timeoutTask;
    }

    public void setTimeoutTask(SchedulerHelper.LovelyTask timeoutTask) {
        this.timeoutTask = timeoutTask;
    }
}
