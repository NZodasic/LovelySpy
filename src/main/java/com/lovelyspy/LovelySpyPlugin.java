package com.lovelyspy;

import com.lovelyspy.command.Commands;
import com.lovelyspy.config.Config;
import com.lovelyspy.config.ConfigDialogManager;
import com.lovelyspy.detection.*;
import com.lovelyspy.offense.OffenseManager;
import com.lovelyspy.integration.LibertyBansHook;
import com.lovelyspy.gui.PlayerInventoryManager;
import com.lovelyspy.util.*;
import io.papermc.paper.ban.BanListType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.*;
import java.time.Duration;

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
    private DiscordBotNotifier discordBotNotifier;
    private LibertyBansHook libertyBansHook;
    private PlayerInventoryManager playerInventoryManager;

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

        discordBotNotifier = new DiscordBotNotifier(this);
        libertyBansHook = new LibertyBansHook(this);
        libertyBansHook.initialize();

        vector1 = new Vector1_TranslationFingerprint(this);
        vector2 = new Vector2_BrandChannelAnalysis(this);
        vector3 = new Vector3_PrivacyModDetection(this);
        vector4 = new Vector4_ResourcePackAltDetection(this);
        commands = new Commands(this);
        configDialogManager = new ConfigDialogManager(this);
        playerInventoryManager = new PlayerInventoryManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(vector2, this);
        getServer().getPluginManager().registerEvents(vector3, this);
        getServer().getPluginManager().registerEvents(playerInventoryManager, this);

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

        if (!config.autoCheckOnJoinEnabled
                || (config.autoCheckOnlyFirstJoin && player.hasPlayedBefore())) {
            return;
        }

        // Schedule translation fingerprinter after probe_delay_ticks
        SchedulerHelper.runTaskLater(this, () -> {
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
        vector2.removeProfile(player.getUniqueId());
    }

    private void injectPlayer(Player player) {
        io.netty.channel.Channel channel = PacketHelper.getChannel(player);
        if (channel != null && channel.pipeline().get("lovelyspy_handler") == null) {
            channel.pipeline().addBefore("packet_handler", "lovelyspy_handler",
                    new PlayerPacketHandler(player));
        }
    }

    private void ejectPlayer(Player player) {
        io.netty.channel.Channel channel = PacketHelper.getChannel(player);
        if (channel != null && channel.pipeline().get("lovelyspy_handler") != null) {
            channel.pipeline().remove("lovelyspy_handler");
        }
    }

    public void executeDetection(Player player, String key, String responseVal, String vectorName) {
        executeDetection(player, key, responseVal, vectorName, "Automatic");
    }

    public void reportInconclusiveScan(Player player, List<String> keysTested,
                                       Map<String, String> responses, String vectorName) {
        Logger.LogEntry logEntry = loggerService.log(player.getUniqueId(), player.getName(),
                new ArrayList<>(keysTested), new LinkedHashMap<>(responses),
                List.of(vectorName), "INCONCLUSIVE", "NONE");

        String alertMsg = buildInconclusiveAlert(player, logEntry.caseId);
        Bukkit.getConsoleSender().sendMessage(alertMsg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (commands.isAlertsEnabled(p)) {
                p.sendMessage(alertMsg);
            }
        }
    }

    public void executeDetection(Player player, String key, String responseVal, String vectorName, String checker) {
        // Find matched ModEntry
        Config.ModEntry matched = null;
        for (Config.ModEntry entry : config.modEntries.values()) {
            if (entry.keys.contains(key)) {
                if (!entry.enabled) return;
                matched = entry;
                break;
            }
            if (entry.vector != null && entry.vector.equalsIgnoreCase("privacy_probe")
                    && isPrivacyProbeEvidence(key)) {
                if (!entry.enabled) return;
                matched = entry;
                break;
            }
        }

        executeDetection(player, List.of(key), Map.of(key, responseVal), key, responseVal,
                vectorName, checker, matched);
    }

    private boolean isPrivacyProbeEvidence(String key) {
        return key.equals("translation_shield")
                || key.equals("opsec_key_resolution_blocked");
    }

    public void executeModDetection(Player player, Config.ModEntry matched,
                                    Map<String, String> evidence, String vectorName, String checker) {
        if (matched == null || !matched.enabled || evidence.isEmpty()) return;

        String evidenceSummary = evidence.entrySet().stream()
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        executeDetection(player, new ArrayList<>(evidence.keySet()), new LinkedHashMap<>(evidence),
                matched.name, evidenceSummary, vectorName, checker, matched);
    }

    private void executeDetection(Player player, List<String> keysTested,
                                  Map<String, String> responses, String evidenceKey,
                                  String evidenceText, String vectorName, String checker,
                                  Config.ModEntry matched) {
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

        String confidence;
        if (evidenceKey.equals("sign_packet_blocked")) confidence = "INCONCLUSIVE";
        else if (vectorName.contains("Translation Fingerprinting")) confidence = "HIGH";
        else if (action.equalsIgnoreCase("BAN")) confidence = "CRITICAL";
        else if (action.equalsIgnoreCase("KICK")) confidence = "HIGH";
        else if (action.equalsIgnoreCase("FLAG")) confidence = "MEDIUM";
        else confidence = "LOW";

        // Log result
        Logger.LogEntry logEntry = loggerService.log(player.getUniqueId(), player.getName(),
                new ArrayList<>(keysTested), new LinkedHashMap<>(responses),
                List.of(vectorName), confidence, action);

        // Broadcast alert
        String alertMsg = buildFriendlyAlert(player, evidenceKey, evidenceText, vectorName,
                confidence, action, matched, logEntry.caseId);
        
        Bukkit.getConsoleSender().sendMessage(alertMsg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (commands.isAlertsEnabled(p)) {
                p.sendMessage(alertMsg);
            }
        }

        discordBotNotifier.sendDetection(player, checker, matched, evidenceKey, evidenceText,
                vectorName, confidence, action, logEntry.caseId, alertMsg);

        // Execute action on primary thread
        String finalAction = action;
        String finalMessage = message;
        SchedulerHelper.runTask(this, () -> {
            if (!player.isOnline()) return;

            switch (finalAction.toUpperCase()) {
                case "KICK":
                    if (config.actionKickEnabled) {
                        player.kick(LegacyComponentSerializer.legacySection().deserialize("§c" + finalMessage));
                    } else {
                        getLogger().info("Action KICK is disabled globally. Skipped kick for " + player.getName());
                    }
                    break;
                case "BAN":
                    if (config.actionBanEnabled) {
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
                        Duration duration = Duration.between(new Date().toInstant(), expires.toInstant());
                        if (!libertyBansHook.tempBan(player, duration, banMessage)) {
                            PlayerProfile profile = player.getPlayerProfile();
                            Bukkit.getBanList(BanListType.PROFILE).addBan(profile, "§c" + banMessage,
                                    expires.toInstant(), "LovelySpy");
                            player.kick(LegacyComponentSerializer.legacySection().deserialize("§c" + banMessage));
                        }
                    } else {
                        getLogger().info("Action BAN is disabled globally. Skipped ban for " + player.getName());
                    }
                    break;
                case "FLAG":
                    if (!config.actionFlagEnabled) {
                        getLogger().info("Action FLAG is disabled globally. Skipped flag action for " + player.getName());
                    }
                    break;
                case "SHADOW":
                    if (!config.actionShadowEnabled) {
                        getLogger().info("Action SHADOW is disabled globally. Skipped shadow action for " + player.getName());
                    }
                    break;
            }
        });
    }

    private String buildFriendlyAlert(Player player, String key, String evidence,
                                      String vectorName, String confidence, String action,
                                      Config.ModEntry matched, long caseId) {
        if (key.equals("sign_packet_blocked")) {
            return buildInconclusiveAlert(player, caseId);
        }
        if (vectorName.contains("Translation Fingerprinting") && matched != null) {
            return "§c[LovelySpy] §7#" + caseId + " §8| §e" + player.getName()
                    + " §8| §cDETECTED: " + humanizeIdentifier(matched.name)
                    + " §8| §c" + confidence + " §8| §b" + describeSystemAction(action);
        }
        return "§c[LovelySpy] §7#" + caseId + " §8| §e" + player.getName()
                + " §8| §cDETECTION: §f" + compactEvidence(evidence)
                + " §8| §6" + confidence + " §8| §b" + describeSystemAction(action);
    }

    private String buildInconclusiveAlert(Player player, long caseId) {
        ClientProfile profile = vector2.getProfile(player);
        String loaders = profile.loaders().isEmpty()
                ? "None detected" : String.join(", ", profile.loaders());
        int today = loggerService.countToday(player.getUniqueId(), "INCONCLUSIVE");
        String environment = compactEnvironment(profile.client(), loaders);
        String watch = today >= 3 ? " §c| WATCHLIST: Review manually" : "";
        return "§c[LovelySpy] §7#" + caseId + " §8| §e" + player.getName()
                + " §8| §6INCONCLUSIVE §8| §b" + environment
                + " §8| §7Scans today: " + today + watch;
    }

    private String compactEnvironment(String client, String loaders) {
        String cleanClient = client.replaceAll("\\s*\\[[^]]+]$", "")
                .replace(" Client", "");
        return loaders.equals("None detected") ? cleanClient : cleanClient + "/" + loaders;
    }

    private String compactEvidence(String evidence) {
        int reason = evidence.indexOf(" | Reason:");
        return reason >= 0 ? evidence.substring(0, reason) : evidence;
    }

    private String describeSystemAction(String action) {
        return switch (action.toUpperCase()) {
            case "KICK" -> config.actionKickEnabled ? "Auto-kick scheduled" : "None (auto-kick disabled)";
            case "BAN" -> config.actionBanEnabled ? "Automatic temporary ban scheduled" : "None (auto-ban disabled)";
            case "FLAG" -> config.actionFlagEnabled ? "Logged and staff alerted" : "Logged (staff flag disabled)";
            case "SHADOW" -> config.actionShadowEnabled ? "Logged for monitoring" : "Logged only";
            default -> "Logged only";
        };
    }

    private String humanizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return "Unknown mod";
        String[] words = identifier.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
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
                if (name.equals("ServerboundSignUpdatePacket")
                        && vector1.isProbing(player.getUniqueId())) {
                    java.lang.reflect.Method getLinesMethod = msg.getClass().getMethod("getLines");
                    String[] lines = (String[]) getLinesMethod.invoke(msg);
                    
                    SchedulerHelper.runTask(LovelySpyPlugin.this, () -> {
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

    public Commands getCommands() {
        return commands;
    }

    public PlayerInventoryManager getPlayerInventoryManager() {
        return playerInventoryManager;
    }

    public LibertyBansHook getLibertyBansHook() {
        return libertyBansHook;
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
