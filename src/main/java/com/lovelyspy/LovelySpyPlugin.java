package com.lovelyspy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class LovelySpyPlugin extends JavaPlugin implements Listener {

    private Config config;
    private Logger loggerService;
    private OffenseManager offenseManager;
    private Vector1_TranslationFingerprint vector1;
    private Vector2_BrandChannelAnalysis vector2;
    private Vector3_PrivacyModDetection vector3;
    private Vector4_ResourcePackAltDetection vector4;
    private Commands commands;
    private ConfigDialogManager configDialogManager;

    @Override
    public void onEnable() {
        // Save and load config
        saveDefaultConfig();
        config = new Config(this);
        config.load();

        // Instantiate modules
        loggerService = new Logger(this);
        loggerService.init();

        offenseManager = new OffenseManager(this);
        offenseManager.init();

        vector1 = new Vector1_TranslationFingerprint(this);
        vector2 = new Vector2_BrandChannelAnalysis(this);
        vector3 = new Vector3_PrivacyModDetection(this);
        vector4 = new Vector4_ResourcePackAltDetection(this);
        commands = new Commands(this);
        configDialogManager = new ConfigDialogManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(vector2, this);
        getServer().getPluginManager().registerEvents(vector3, this);

        // Register commands
        getCommand("lovelyspy").setExecutor(commands);
        getCommand("lovelyspy").setTabCompleter(commands);

        // Inject existing online players (if reloaded)
        for (Player player : Bukkit.getOnlinePlayers()) {
            injectPlayer(player);
        }

        getLogger().info("LovelySpy Client Detection Engine successfully enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up Netty handlers
        for (Player player : Bukkit.getOnlinePlayers()) {
            ejectPlayer(player);
        }

        // Cleanup modules
        vector1.cleanup();
        vector3.cleanup();
        vector4.cleanup();

        getLogger().info("LovelySpy Client Detection Engine successfully disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        injectPlayer(player);

        // Schedule translation fingerprinter after probe_delay_ticks
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                vector1.probe(player);
            }
        }, config.probeDelayTicks);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ejectPlayer(player);
        vector1.handleQuit(player.getUniqueId());
    }

    private void injectPlayer(Player player) {
        io.netty.channel.Channel channel = PacketHelper.getChannel(player);
        if (channel != null && channel.pipeline().get("lovelyspy_handler") == null) {
            channel.pipeline().addBefore("packet_handler", "lovelyspy_handler", new PlayerPacketHandler(player));
        }
    }

    private void ejectPlayer(Player player) {
        io.netty.channel.Channel channel = PacketHelper.getChannel(player);
        if (channel != null && channel.pipeline().get("lovelyspy_handler") != null) {
            channel.pipeline().remove("lovelyspy_handler");
        }
    }

    public void executeDetection(Player player, String key, String responseVal, String vectorName) {
        // Find matched ModEntry
        Config.ModEntry matched = null;
        for (Config.ModEntry entry : config.modEntries.values()) {
            if (entry.keys != null && entry.keys.contains(key)) {
                matched = entry;
                break;
            }
            if (entry.vector != null && entry.vector.equalsIgnoreCase("privacy_probe") 
                    && (key.equals("translation_shield") || key.equals("sign_packet_blocked"))) {
                matched = entry;
                break;
            }
        }

        String action = "FLAG";
        String message = "LovelySpy: Mod/Client detected";
        if (matched != null) {
            action = matched.action;
            message = matched.message;
        } else {
            if (vectorName.contains("Vector 2")) {
                action = "KICK";
                message = "LovelySpy: Prohibited brand or channel detected";
            } else if (vectorName.contains("NoChatReports")) {
                action = "FLAG";
                message = "LovelySpy: Unsigned chat reports";
            }
        }

        String confidence = "HIGH";
        if (action.equalsIgnoreCase("BAN")) confidence = "CRITICAL";
        else if (action.equalsIgnoreCase("KICK")) confidence = "HIGH";
        else if (action.equalsIgnoreCase("FLAG")) confidence = "MEDIUM";
        else if (action.equalsIgnoreCase("SHADOW")) confidence = "LOW";

        // Log result
        loggerService.log(player.getUniqueId(), player.getName(), 
                List.of(key), Map.of(key, responseVal), 
                List.of(vectorName), confidence, action);

        // Broadcast alert
        String alertMsg = "§c[LovelySpy] §e" + player.getName() + " §7triggered §b" + vectorName + 
                " §7(§f" + key + " §7-> §f" + responseVal + "§7) §6[" + confidence + "]";
        
        Bukkit.getConsoleSender().sendMessage(alertMsg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (commands.isAlertsEnabled(p)) {
                p.sendMessage(alertMsg);
            }
        }

        // Execute action on primary thread
        String finalAction = action;
        String finalMessage = message;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;

            switch (finalAction.toUpperCase()) {
                case "KICK":
                    player.kickPlayer("§c" + finalMessage);
                    break;
                case "BAN":
                    int count = offenseManager.incrementOffenseCount(player.getUniqueId());
                    Date expires = offenseManager.getBanExpiration(count);
                    String durationStr = switch (count) {
                        case 1 -> "15 minutes";
                        case 2 -> "30 minutes";
                        case 3 -> "1 day";
                        case 4 -> "3 days";
                        default -> "30 days";
                    };
                    String banMessage = finalMessage + "\n§7Offense #" + count + " - Banned for " + durationStr;
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), "§c" + banMessage, expires, "LovelySpy");
                    player.kickPlayer("§c" + banMessage);
                    break;
            }
        });
    }

    // Packet interceptor logic using Netty ChannelDuplexHandler
    private class PlayerPacketHandler extends io.netty.channel.ChannelDuplexHandler {
        private final Player player;

        public PlayerPacketHandler(Player player) {
            this.player = player;
        }

        @Override
        public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                String name = msg.getClass().getSimpleName();
                if (name.equals("ServerboundSignUpdatePacket")) {
                    java.lang.reflect.Method getLinesMethod = msg.getClass().getMethod("getLines");
                    String[] lines = (String[]) getLinesMethod.invoke(msg);
                    
                    Bukkit.getScheduler().runTask(LovelySpyPlugin.this, () -> {
                        vector1.handleResponse(player.getUniqueId(), lines);
                    });
                    return; // consume packet
                } else if (name.equals("ServerboundChatPacket")) {
                    vector3.handleChatPacket(player, msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) throws Exception {
            if (msg.getClass().getSimpleName().equals("ClientboundResourcePackPushPacket")) {
                vector3.recordPackSent(player.getUniqueId());
            }
            super.write(ctx, msg, promise);
        }
    }

    public Config getLovelyConfig() {
        return config;
    }

    public Logger getLoggerService() {
        return loggerService;
    }

    public OffenseManager getOffenseManager() {
        return offenseManager;
    }

    public ConfigDialogManager getConfigDialogManager() {
        return configDialogManager;
    }

    public Vector1_TranslationFingerprint getVector1() {
        return vector1;
    }

    public Vector2_BrandChannelAnalysis getVector2() {
        return vector2;
    }

    public Vector3_PrivacyModDetection getVector3() {
        return vector3;
    }

    public Vector4_ResourcePackAltDetection getVector4() {
        return vector4;
    }
}
