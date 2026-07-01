package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Vector3_PrivacyModDetection implements Listener {
    private final LovelySpyPlugin plugin;
    
    private final Map<UUID, Integer> unsignedMessageCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> packSentTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> packAcceptTimes = new ConcurrentHashMap<>();
    private final Set<UUID> flaggedResourcePackSpoof = ConcurrentHashMap.newKeySet();

    public Vector3_PrivacyModDetection(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void flagTranslationShield(Player player) {
        flagTranslationShield(player, "Automatic");
    }

    public void flagTranslationShield(Player player, String checker) {
        plugin.executeDetection(player, "translation_shield", "Canary translation key did not resolve (shield active)", "Vector 3 (Translation Shield)", checker);
    }

    public void flagKeyResolutionShield(Player player, String evidence, String checker) {
        boolean resourcePackSpoofed = flaggedResourcePackSpoof.contains(player.getUniqueId());
        String vectorName = "Vector 3 (OpSec / ExploitPreventer Key-Resolution Shield)";
        if (resourcePackSpoofed) {
            vectorName += " [CONFIRMED: Resource Pack Spoof Correlated]";
        }
        plugin.executeDetection(player, "opsec_key_resolution_blocked",
                "Repeated vanilla key-resolution block: " + evidence + (resourcePackSpoofed ? " | Correlated with instant resource pack accept" : ""),
                vectorName, checker);
    }

    public void flagSignTimeout(Player player) {
        flagSignTimeout(player, "Automatic");
    }

    public void flagSignTimeout(Player player, String checker) {
        plugin.executeDetection(player, "sign_packet_blocked",
                "The client did not answer the hidden mod scan. Possible causes: network lag, "
                        + "packet filtering, or client privacy/protection. No specific hack was identified.",
                "Hidden Mod Scan (No Response)", checker);
    }

    public void recordPackSent(UUID uuid) {
        packSentTimes.put(uuid, System.currentTimeMillis());
    }

    public void handleChatPacket(Player player, Object packet) {
        try {
            // Check if player has a chat session (chat signing capability)
            Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object chatSession = nmsPlayer.getClass().getMethod("getChatSession").invoke(nmsPlayer);
            if (chatSession == null) {
                return;
            }

            // Get signature from ServerboundChatPacket
            Method signatureMethod = packet.getClass().getMethod("signature");
            Object signature = signatureMethod.invoke(packet);
            
            boolean hasSignature = false;
            if (signature != null) {
                Method bytesMethod = signature.getClass().getMethod("bytes");
                byte[] bytes = (byte[]) bytesMethod.invoke(signature);
                if (bytes != null && bytes.length > 0) {
                    hasSignature = true;
                }
            }

            if (!hasSignature) {
                int count = unsignedMessageCount.getOrDefault(player.getUniqueId(), 0) + 1;
                unsignedMessageCount.put(player.getUniqueId(), count);
                if (count >= 3) {
                    plugin.executeDetection(player, "no_chat_reports", "Sent 3+ unsigned chat messages with signing capability", "Vector 3 (NoChatReports/OpSec)");
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("lovelyspy.bypass")) return;

        UUID uuid = player.getUniqueId();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        if (status == PlayerResourcePackStatusEvent.Status.ACCEPTED) {
            Long sentTime = packSentTimes.get(uuid);
            if (sentTime != null) {
                long duration = System.currentTimeMillis() - sentTime;
                packAcceptTimes.put(uuid, System.currentTimeMillis());
                if (duration < 15) { // Physically impossible for normal clients
                    flaggedResourcePackSpoof.add(uuid);
                    plugin.executeDetection(player, "resource_pack_spoof_accept", 
                            "Accepted resource pack in " + duration + "ms (Auto-Accept Spoof)", "Vector 3 (Resource Pack Spoof)");
                }
            }
        } else if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            Long acceptTime = packAcceptTimes.get(uuid);
            if (acceptTime != null) {
                long duration = System.currentTimeMillis() - acceptTime;
                plugin.getVector4().recordPackLoaded(player, duration);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        unsignedMessageCount.remove(uuid);
        packSentTimes.remove(uuid);
        packAcceptTimes.remove(uuid);
        flaggedResourcePackSpoof.remove(uuid);
    }

    public void cleanup() {
        unsignedMessageCount.clear();
        packSentTimes.clear();
        packAcceptTimes.clear();
        flaggedResourcePackSpoof.clear();
    }
}
