package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.util.SchedulerHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import java.util.*;

public final class Vector2_BrandChannelAnalysis implements Listener {
    private final LovelySpyPlugin plugin;
    private final Map<UUID, ClientProfile> profiles = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> emittedDetections = new java.util.concurrent.ConcurrentHashMap<>();

    public Vector2_BrandChannelAnalysis(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("lovelyspy.bypass")) {
            return;
        }
        ClientProfile profile = profiles.computeIfAbsent(player.getUniqueId(), ClientProfile::new);
        detectBedrock(player, profile);

        // Schedule check 30 ticks after join to ensure client brand & channels are fully loaded
        SchedulerHelper.runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                performAnalysis(player);
            }
        }, 30L);
    }

    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("lovelyspy.bypass")) {
            return;
        }

        String channel = event.getChannel();
        ClientProfile profile = profiles.computeIfAbsent(player.getUniqueId(), ClientProfile::new);
        profile.addChannel(channel);
        classifyChannel(profile, channel);
        
        // Immediate check on the registered channel
        if (isKnownCheatChannel(channel) && markNewDetection(player, "channel:" + channel)) {
            plugin.executeDetection(player, "channel:" + channel, describeChannel(channel),
                    "Vector 2 (Known Cheat Channel Registered)");
        }
    }

    public void performAnalysis(Player player) {
        String brand = player.getClientBrandName();
        if (brand == null) brand = "unknown";

        Set<String> channels = player.getListeningPluginChannels();
        ClientProfile profile = profiles.computeIfAbsent(player.getUniqueId(), ClientProfile::new);
        profile.setBrand(brand);
        classifyBrand(profile, brand);
        for (String channel : channels) {
            profile.addChannel(channel);
            classifyChannel(profile, channel);
        }

        // 1. Check if brand is a known cheat brand
        for (String cheatBrand : plugin.getLovelyConfig().knownCheatBrands) {
            if (brand.equalsIgnoreCase(cheatBrand)) {
                plugin.executeDetection(player, "brand:" + brand,
                        "Detected client: " + friendlyBrand(brand) + " (prohibited client brand)",
                        "Vector 2 (Known Cheat Brand)");
                return;
            }
        }

        // 2. Check if registered channels contain known cheat channels
        for (String channel : channels) {
            if (isKnownCheatChannel(channel)
                    && markNewDetection(player, "channel:" + channel)) {
                plugin.executeDetection(player, "channel:" + channel, describeChannel(channel),
                        "Vector 2 (Known Cheat Channel Registered)");
                return;
            }
        }

        // 3. Spoof check. A custom channel is not suspicious by itself: legitimate
        // Fabric/Forge mods (voice chat, maps, performance mods, etc.) use them.
        // Only flag when a client explicitly claims to be vanilla while also
        // exposing a loader-specific protocol channel.
        if (brand.equalsIgnoreCase("vanilla")) {
            for (String channel : channels) {
                if (isLoaderProtocolChannel(channel)
                        && markNewDetection(player, "mismatch:brand=" + brand + ",channel=" + channel)) {
                plugin.executeDetection(player, "mismatch:brand=" + brand + ",channel=" + channel,
                            describeMismatch(brand, channel), "Vector 2 (Brand Spoof Mismatch)");
                    return;
                }
            }
        }
    }

    private String describeMismatch(String brand, String channel) {
        return "Detected client: " + friendlyBrand(brand)
                + " | Registered channel: " + friendlyChannel(channel)
                + " | Reason: unrecognized client/channel combination";
    }

    private String describeChannel(String channel) {
        String mod = switch (channel.toLowerCase(Locale.ROOT)) {
            case "wdl|init" -> "World Downloader";
            case "seedcracker" -> "SeedCracker";
            default -> "Unknown mod";
        };
        return "Detected mod: " + mod + " | Registered channel: " + channel
                + " (prohibited channel)";
    }

    private String friendlyBrand(String brand) {
        String lower = brand.toLowerCase(Locale.ROOT);
        if (lower.startsWith("lunarclient:")) {
            String details = brand.substring("lunarclient:".length());
            String[] parts = details.split(",", 2);
            String version = parts[0].replaceFirst("^[vV]", "");
            String loader = parts.length > 1 ? " / " + capitalize(parts[1]) : "";
            return "Lunar Client " + version + loader + " [" + brand + "]";
        }
        return brand;
    }

    private String friendlyChannel(String channel) {
        if (channel.equalsIgnoreCase("apollo:json")) {
            return "Lunar Apollo services [" + channel + "]";
        }
        return channel;
    }

    private String capitalize(String value) {
        if (value.isBlank()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public ClientProfile getProfile(Player player) {
        ClientProfile profile = profiles.computeIfAbsent(player.getUniqueId(), ClientProfile::new);
        String brand = player.getClientBrandName();
        profile.setBrand(brand);
        classifyBrand(profile, brand);
        for (String channel : player.getListeningPluginChannels()) {
            profile.addChannel(channel);
            classifyChannel(profile, channel);
        }
        detectBedrock(player, profile);
        return profile;
    }

    public void removeProfile(UUID uuid) {
        profiles.remove(uuid);
        emittedDetections.remove(uuid);
    }

    public Collection<ClientProfile> getProfiles() {
        return List.copyOf(profiles.values());
    }

    private void classifyBrand(ClientProfile profile, String brand) {
        if (brand == null) return;
        String lower = brand.toLowerCase(Locale.ROOT);
        if (lower.contains("lunarclient")) profile.setClient(friendlyBrand(brand));
        else if (lower.contains("badlion")) profile.setClient("Badlion Client");
        else if (lower.contains("feather")) profile.setClient("Feather Client");
        else if (lower.contains("laby")) profile.setClient("LabyMod");
        else if (lower.contains("geyser")) profile.markBedrock("Geyser brand");
        else if (lower.equals("vanilla")) profile.setClient("Vanilla");

        if (lower.contains("neoforge")) profile.addLoader("NeoForge");
        else if (lower.contains("forge")) profile.addLoader("Forge");
        if (lower.contains("fabric")) profile.addLoader("Fabric");
        if (lower.contains("liteloader")) profile.addLoader("LiteLoader");
        if (lower.contains("optifine")) profile.addMod("OptiFine");
    }

    private void classifyChannel(ClientProfile profile, String channel) {
        String lower = channel.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "labymod", "lmc", "labymod3:main" -> profile.setClient("LabyMod");
            case "5zig_set" -> profile.setClient("5zig Mod");
            case "blc|m" -> profile.setClient("Badlion Client");
            case "hyperium" -> profile.setClient("Hyperium");
            case "wdl|init", "wdl|control" -> profile.addMod("World Downloader");
            case "journeymap_channel" -> profile.addMod("JourneyMap");
            case "xaerominimap" -> profile.addMod("Xaero's Minimap");
            case "inventorytweaks" -> profile.addMod("Inventory Tweaks");
            case "wecui" -> profile.addMod("WorldEdit CUI");
            case "bsprint" -> profile.addMod("Better Sprinting");
            case "waila" -> profile.addMod("WAILA");
            case "voicechat:add_category", "voicechat:main" -> profile.addMod("Simple Voice Chat");
            case "lolimahcker" -> profile.addMod("Cracked Vape");
            case "l:fmlhs", "fml|hs" -> profile.addLoader("Forge");
            case "lunar:apollo", "apollo:json" -> {
                if (profile.client().equals("Unknown")) profile.setClient("Lunar Client");
            }
            default -> classifyModNamespace(profile, lower);
        }
    }

    private void classifyModNamespace(ClientProfile profile, String channel) {
        int separator = channel.indexOf(':');
        if (separator <= 0) return;
        String namespace = channel.substring(0, separator);
        if (Set.of("minecraft", "bungeecord", "paper", "velocity", "apollo", "lunar").contains(namespace)) {
            return;
        }
        if (namespace.equals("fabric")) {
            profile.addLoader("Fabric");
        } else if (namespace.equals("forge") || namespace.equals("fml")) {
            profile.addLoader("Forge");
        } else if (namespace.equals("neoforge")) {
            profile.addLoader("NeoForge");
        } else {
            profile.addMod(namespace);
        }
    }

    private void detectBedrock(Player player, ClientProfile profile) {
        if (invokeBedrockApi("org.geysermc.geyser.api.GeyserApi", "api",
                "isBedrockPlayer", player.getUniqueId())) {
            profile.markBedrock("Geyser");
        } else if (invokeBedrockApi("org.geysermc.floodgate.api.FloodgateApi", "getInstance",
                "isFloodgatePlayer", player.getUniqueId())) {
            profile.markBedrock("Floodgate");
        }
    }

    private boolean invokeBedrockApi(String className, String instanceMethod,
                                     String checkMethod, UUID uuid) {
        try {
            Class<?> apiClass = Class.forName(className);
            Object api = apiClass.getMethod(instanceMethod).invoke(null);
            Object result = apiClass.getMethod(checkMethod, UUID.class).invoke(api, uuid);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private boolean isKnownCheatChannel(String channel) {
        return plugin.getLovelyConfig().knownCheatChannels.stream()
                .anyMatch(known -> known.equalsIgnoreCase(channel));
    }

    private boolean isLoaderProtocolChannel(String channel) {
        String lower = channel.toLowerCase(Locale.ROOT);
        return lower.equals("fml|hs")
                || lower.equals("fml:handshake")
                || lower.equals("forge:handshake")
                || lower.equals("neoforge:handshake")
                || lower.startsWith("fabric:");
    }

    private boolean markNewDetection(Player player, String key) {
        return emittedDetections.computeIfAbsent(player.getUniqueId(),
                ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(key.toLowerCase(Locale.ROOT));
    }
}
