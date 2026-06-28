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

    public Vector2_BrandChannelAnalysis(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("lovelyspy.bypass")) {
            return;
        }

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
        
        // Immediate check on the registered channel
        if (plugin.getLovelyConfig().knownCheatChannels.contains(channel)) {
            plugin.executeDetection(player, "channel:" + channel, channel, "Vector 2 (Known Cheat Channel Registered)");
        }
    }

    public void performAnalysis(Player player) {
        String brand = player.getClientBrandName();
        if (brand == null) brand = "unknown";

        Set<String> channels = player.getListeningPluginChannels();

        // 1. Check if brand is a known cheat brand
        for (String cheatBrand : plugin.getLovelyConfig().knownCheatBrands) {
            if (brand.equalsIgnoreCase(cheatBrand)) {
                plugin.executeDetection(player, "brand:" + brand, brand, "Vector 2 (Known Cheat Brand)");
                return;
            }
        }

        // 2. Check if registered channels contain known cheat channels
        for (String channel : channels) {
            if (plugin.getLovelyConfig().knownCheatChannels.contains(channel)) {
                plugin.executeDetection(player, "channel:" + channel, channel, "Vector 2 (Known Cheat Channel Registered)");
                return;
            }
        }

        // 3. Mismatch check: if brand is "vanilla" or "spigot" but registers strange client mods
        // Typically, vanilla clients do not register custom mod channels (unless they use a whitelisted client).
        boolean isWhitelisted = false;
        String lowerBrand = brand.toLowerCase();
        for (String allowed : plugin.getLovelyConfig().legitimateBrands) {
            if (lowerBrand.contains(allowed.toLowerCase()) || lowerBrand.equals("vanilla")) {
                isWhitelisted = true;
                break;
            }
        }

        if (!isWhitelisted) {
            // Check if they registered custom channels
            for (String channel : channels) {
                // Ignore standard minecraft channels and BungeeCord
                if (channel.startsWith("minecraft:") || channel.equalsIgnoreCase("BungeeCord")) {
                    continue;
                }
                // Flag mismatch
                plugin.executeDetection(player, "mismatch:brand=" + brand + ",channel=" + channel, 
                        "Brand: " + brand + ", Channel: " + channel, "Vector 2 (Brand/Channel Mismatch)");
                return;
            }
        }
    }
}
