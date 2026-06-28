package com.lovelyspy.command;

import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.detection.ClientProfile;
import com.lovelyspy.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Commands implements CommandExecutor, TabCompleter {
    private final LovelySpyPlugin plugin;
    private final Set<UUID> alertsToggled = new HashSet<>();

    public Commands(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAlertsEnabled(Player player) {
        // By default, players with permission receive alerts, unless they toggle them off
        return player.hasPermission("lovelyspy.alerts") && !alertsToggled.contains(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("lovelyspy.reload")) {
                    sender.sendMessage("§cYou do not have permission to reload the configuration.");
                    return true;
                }
                plugin.getLovelyConfig().reload();
                plugin.getLoggerService().init();
                sender.sendMessage("§a[LovelySpy] Configuration and logs reloaded successfully.");
                return true;

            case "check":
                if (!sender.hasPermission("lovelyspy.check")) {
                    sender.sendMessage("§cYou do not have permission to run checks.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lovelyspy check <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage("§cPlayer not found or offline.");
                    return true;
                }
                if (!plugin.getLovelyConfig().translationProbeEnabled) {
                    sender.sendMessage("§c[LovelySpy] Translation probing is disabled because it is "
                            + "not reliable with the current packet-protection stack.");
                    return true;
                }
                sender.sendMessage("§e[LovelySpy] Starting manual probe on " + target.getName() + "...");
                plugin.getVector1().probe(target, sender.getName());
                return true;

            case "info":
                if (!sender.hasPermission("lovelyspy.check")) {
                    sender.sendMessage("§cYou do not have permission to view player info.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lovelyspy info <player>");
                    return true;
                }
                Player infoTarget = Bukkit.getPlayer(args[1]);
                if (infoTarget == null || !infoTarget.isOnline()) {
                    sender.sendMessage("§cPlayer not found or offline.");
                    return true;
                }
                showPlayerInfo(sender, infoTarget);
                return true;

            case "list":
                if (!sender.hasPermission("lovelyspy.check")) {
                    sender.sendMessage("§cYou do not have permission to view client profiles.");
                    return true;
                }
                showPlayerList(sender);
                return true;

            case "history":
                if (!sender.hasPermission("lovelyspy.check")) {
                    sender.sendMessage("§cYou do not have permission to view history.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lovelyspy history <player>");
                    return true;
                }
                showHistory(sender, args[1]);
                return true;

            case "alerts":
                if (!sender.hasPermission("lovelyspy.alerts")) {
                    sender.sendMessage("§cYou do not have permission to toggle alerts.");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can toggle alerts.");
                    return true;
                }
                UUID uuid = player.getUniqueId();
                if (alertsToggled.contains(uuid)) {
                    alertsToggled.remove(uuid);
                    sender.sendMessage("§a[LovelySpy] Live alerts enabled.");
                } else {
                    alertsToggled.add(uuid);
                    sender.sendMessage("§c[LovelySpy] Live alerts disabled.");
                }
                return true;

            case "offenses":
                if (!sender.hasPermission("lovelyspy.check")) {
                    sender.sendMessage("§cYou do not have permission to view offenses.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lovelyspy offenses <player>");
                    return true;
                }
                org.bukkit.OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(args[1]);
                if (offPlayer == null || offPlayer.getUniqueId() == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                int offCount = plugin.getOffenseManager().getOffenseCount(offPlayer.getUniqueId());
                sender.sendMessage("§e[LovelySpy] " + offPlayer.getName() + " has §c" + offCount + " §eoffenses.");
                return true;

            case "reset":
            case "resetoffense":
                if (!sender.hasPermission("lovelyspy.reset")) {
                    sender.sendMessage("§cYou do not have permission to reset offenses.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lovelyspy resetoffense <player>");
                    return true;
                }
                org.bukkit.OfflinePlayer resetPlayer = Bukkit.getOfflinePlayer(args[1]);
                if (resetPlayer == null || resetPlayer.getUniqueId() == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                plugin.getOffenseManager().clearOffenseCount(resetPlayer.getUniqueId());
                sender.sendMessage("§a[LovelySpy] Cleared offense count for " + resetPlayer.getName() + ".");
                return true;

            case "gui":
            case "config":
            case "dialog":
                if (!sender.hasPermission("lovelyspy.admin")) {
                    sender.sendMessage("§cYou do not have permission to open the config GUI.");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can open the config GUI.");
                    return true;
                }
                plugin.getConfigDialogManager().openMainMenu(player);
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== LovelySpy Commands ===");
        sender.sendMessage("§e/lovelyspy check <player> §7- Manually probe a player");
        sender.sendMessage("§e/lovelyspy info <player> §7- View player client info");
        sender.sendMessage("§e/lovelyspy list §7- List detected clients and loaders");
        sender.sendMessage("§e/lovelyspy history <player> §7- View player check history");
        sender.sendMessage("§e/lovelyspy offenses <player> §7- View player offense count");
        sender.sendMessage("§e/lovelyspy resetoffense <player> §7- Reset player offense count");
        sender.sendMessage("§e/lovelyspy gui/config §7- Open dialog configuration menu");
        sender.sendMessage("§e/lovelyspy alerts §7- Toggle live detection alerts");
        sender.sendMessage("§e/lovelyspy reload §7- Reload configuration");
    }

    private void showPlayerInfo(CommandSender sender, Player target) {
        ClientProfile profile = plugin.getVector2().getProfile(target);
        Set<String> channels = profile.channels();
        sender.sendMessage("§6§l=== LovelySpy Profile: " + target.getName() + " ===");
        sender.sendMessage("§eUUID: §7" + target.getUniqueId());
        sender.sendMessage("§eIP: §7" + (target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "unknown"));
        sender.sendMessage("§ePlatform: §b" + profile.platform()
                + (profile.bedrockSource() != null ? " §7(" + profile.bedrockSource() + ")" : ""));
        sender.sendMessage("§eDetected Client: §b" + profile.client());
        sender.sendMessage("§eRaw Brand: §7" + profile.brand());
        sender.sendMessage("§eLoaders: §b" + formatValues(profile.loaders()));
        sender.sendMessage("§eDetected Mods: §b" + formatValues(profile.mods()));
        sender.sendMessage("§eRegistered Channels (" + channels.size() + "):");
        if (channels.isEmpty()) {
            sender.sendMessage("  - None");
        } else {
            for (String ch : channels) {
                sender.sendMessage("  - " + ch);
            }
        }
    }

    private void showPlayerList(CommandSender sender) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        sender.sendMessage("§6§l=== LovelySpy Online Client Profiles (" + players.size() + ") ===");
        for (Player player : players) {
            ClientProfile profile = plugin.getVector2().getProfile(player);
            String loaders = profile.loaders().isEmpty()
                    ? "" : " §7[" + String.join(", ", profile.loaders()) + "]";
            String mods = profile.mods().isEmpty()
                    ? "" : " §6⚠ " + String.join(", ", profile.mods());
            sender.sendMessage("§e" + player.getName() + " §7— §b"
                    + profile.client() + loaders + mods);
        }
    }

    private String formatValues(Set<String> values) {
        return values.isEmpty() ? "None detected" : String.join(", ", values);
    }

    private void showHistory(CommandSender sender, String query) {
        List<Logger.LogEntry> logs = plugin.getLoggerService().getHistory(query);
        if (logs.isEmpty()) {
            sender.sendMessage("§cNo history found for " + query);
            return;
        }

        sender.sendMessage("§6§l=== LovelySpy History for: " + query + " ===");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = Math.max(0, logs.size() - 5); i < logs.size(); i++) {
            Logger.LogEntry log = logs.get(i);
            String date = sdf.format(new Date(log.timestamp));
            sender.sendMessage("§e[Case #" + log.caseId + " · " + date + "] §fConfidence: "
                    + getConfidenceColor(log.confidence) + log.confidence + " §fSystem Action: §7" + log.actionTaken);
            sender.sendMessage("  §7Vectors: " + String.join(", ", log.vectorsTriggered));
            if (!log.responses.isEmpty()) {
                sender.sendMessage("  §7Responses: " + log.responses);
            }
        }
    }

    private String getConfidenceColor(String confidence) {
        return switch (confidence.toUpperCase()) {
            case "CLEAN" -> "§a";
            case "LOW" -> "§e";
            case "MEDIUM" -> "§6";
            case "HIGH" -> "§c";
            case "CRITICAL" -> "§4§l";
            default -> "§7";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("lovelyspy.check")) {
                subs.add("check");
                subs.add("info");
                subs.add("list");
                subs.add("history");
                subs.add("offenses");
            }
            if (sender.hasPermission("lovelyspy.alerts")) {
                subs.add("alerts");
            }
            if (sender.hasPermission("lovelyspy.reload")) {
                subs.add("reload");
            }
            if (sender.hasPermission("lovelyspy.admin")) {
                subs.add("gui");
                subs.add("config");
                subs.add("dialog");
            }
            if (sender.hasPermission("lovelyspy.reset")) {
                subs.add("resetoffense");
            }
            return filter(subs, args[0]);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("check") || sub.equals("info") || sub.equals("history") || sub.equals("offenses") || sub.equals("resetoffense")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return filter(players, args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String query) {
        String lower = query.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}
