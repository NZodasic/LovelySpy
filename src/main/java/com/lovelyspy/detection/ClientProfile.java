package com.lovelyspy.detection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientProfile {
    private final UUID uuid;
    private volatile String brand = "unknown";
    private volatile String client = "Unknown";
    private volatile String platform = "Java";
    private volatile String bedrockSource;
    private final Set<String> loaders = ConcurrentHashMap.newKeySet();
    private final Set<String> mods = ConcurrentHashMap.newKeySet();
    private final Set<String> channels = ConcurrentHashMap.newKeySet();

    public ClientProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String brand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand == null || brand.isBlank() ? "unknown" : brand;
    }

    public String client() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String platform() {
        return platform;
    }

    public String bedrockSource() {
        return bedrockSource;
    }

    public void markBedrock(String source) {
        platform = "Bedrock";
        bedrockSource = source;
        client = "Minecraft Bedrock";
    }

    public Set<String> loaders() {
        return snapshot(loaders);
    }

    public Set<String> mods() {
        return snapshot(mods);
    }

    public Set<String> channels() {
        return snapshot(channels);
    }

    public void addLoader(String loader) {
        if (loader != null && !loader.isBlank()) loaders.add(loader);
    }

    public void addMod(String mod) {
        if (mod != null && !mod.isBlank()) mods.add(mod);
    }

    public void addChannel(String channel) {
        if (channel != null && !channel.isBlank()) channels.add(channel);
    }

    private Set<String> snapshot(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
