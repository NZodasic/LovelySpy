package com.lovelyspy;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProbeSession {
    private final UUID uuid;
    private final String name;
    private final Location location;
    private final List<String> testedKeys;
    private final List<String> allPendingKeys;
    private final List<String> flaggedKeys;
    private final long startTime;
    private final boolean isConfirmation;
    
    private SchedulerHelper.LovelyTask timeoutTask;

    public ProbeSession(UUID uuid, String name, Location location, List<String> testedKeys, 
                        List<String> allPendingKeys, List<String> flaggedKeys, boolean isConfirmation) {
        this.uuid = uuid;
        this.name = name;
        this.location = location;
        this.testedKeys = testedKeys;
        this.allPendingKeys = allPendingKeys != null ? allPendingKeys : new ArrayList<>();
        this.flaggedKeys = flaggedKeys != null ? flaggedKeys : new ArrayList<>();
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
