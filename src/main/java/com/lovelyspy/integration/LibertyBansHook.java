package com.lovelyspy.integration;

import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.util.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Optional LibertyBans API bridge. Reflection keeps LovelySpy loadable when
 * LibertyBans is not installed while still using its native punishment API.
 */
public final class LibertyBansHook {
    private final LovelySpyPlugin plugin;
    private Object api;
    private boolean installed;

    public LibertyBansHook(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("LibertyBans") == null) {
            plugin.getLogger().info("LibertyBans not found; Paper ban storage will be used.");
            return;
        }
        installed = true;
        try {
            Class<?> omnibusProvider = Class.forName("space.arim.omnibus.OmnibusProvider");
            Object omnibus = omnibusProvider.getMethod("getOmnibus").invoke(null);
            Class<?> omnibusType = Class.forName("space.arim.omnibus.Omnibus");
            Object registry = omnibusType.getMethod("getRegistry").invoke(omnibus);
            Class<?> registryType = Class.forName("space.arim.omnibus.registry.Registry");
            Class<?> libertyBansClass = Class.forName("space.arim.libertybans.api.LibertyBans");
            Object provider = registryType.getMethod("getProvider", Class.class)
                    .invoke(registry, libertyBansClass);
            api = ((Optional<?>) provider).orElse(null);
            if (api != null) {
                plugin.getLogger().info("LibertyBans integration enabled.");
            } else {
                plugin.getLogger().warning("LibertyBans is installed but its API provider is unavailable.");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not connect to LibertyBans API: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return api != null;
    }

    public boolean tempBan(Player player, Duration duration, String reason) {
        if (api == null) {
            return dispatchCommandFallback(player, duration, reason);
        }
        try {
            Class<?> punishmentType = Class.forName("space.arim.libertybans.api.PunishmentType");
            Class<?> victimType = Class.forName("space.arim.libertybans.api.Victim");
            Class<?> operatorType = Class.forName("space.arim.libertybans.api.Operator");
            Class<?> playerVictim = Class.forName("space.arim.libertybans.api.PlayerVictim");
            Class<?> consoleOperator = Class.forName("space.arim.libertybans.api.ConsoleOperator");
            Class<?> libertyBansType = Class.forName("space.arim.libertybans.api.LibertyBans");
            Class<?> drafterType = Class.forName(
                    "space.arim.libertybans.api.punish.PunishmentDrafter");
            Class<?> builderType = Class.forName(
                    "space.arim.libertybans.api.punish.DraftPunishmentBuilder");
            Class<?> draftType = Class.forName(
                    "space.arim.libertybans.api.punish.DraftPunishment");

            Object ban = enumValue(punishmentType, "BAN");
            Object victim = playerVictim.getMethod("of", java.util.UUID.class)
                    .invoke(null, player.getUniqueId());
            Object operator = consoleOperator.getField("INSTANCE").get(null);
            Object drafter = libertyBansType.getMethod("getDrafter").invoke(api);
            Object builder = drafterType.getMethod("draftBuilder").invoke(drafter);

            builder = invokeBuilder(builderType, builder, "type", punishmentType, ban);
            builder = invokeBuilder(builderType, builder, "victim", victimType, victim);
            builder = invokeBuilder(builderType, builder, "operator", operatorType, operator);
            builder = invokeBuilder(builderType, builder, "reason", String.class, reason);
            builder = invokeBuilder(builderType, builder, "duration", Duration.class, duration);
            Object draft = builderType.getMethod("build").invoke(builder);
            Object stage = draftType.getMethod("enactPunishment").invoke(draft);
            if (observeResult(player, duration, reason, stage)) {
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("LibertyBans API punishment failed: "
                    + describeFailure(exception) + "; trying its command interface.");
        }
        return dispatchCommandFallback(player, duration, reason);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object enumValue(Class<?> type, String value) {
        return Enum.valueOf((Class<? extends Enum>) type, value);
    }

    private Object invokeBuilder(Class<?> builderType, Object builder, String name,
                                 Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        // The runtime implementation in LibertyBans 1.1.2 is package-private.
        // Invoking Method objects obtained from that implementation is rejected
        // by Java 25 even though its methods are public. Resolve methods from the
        // public API interface and invoke them on the implementation instance.
        Method method = builderType.getMethod(name, parameter);
        return method.invoke(builder, value);
    }

    private boolean observeResult(Player player, Duration duration, String reason, Object stage) {
        if (!(stage instanceof CompletionStage<?> completionStage)) {
            plugin.getLogger().warning("LibertyBans returned an unknown punishment result type.");
            return false;
        }
        completionStage.whenComplete((result, failure) -> {
            if (failure != null) {
                plugin.getLogger().warning("LibertyBans could not enact the ban for "
                        + player.getName() + ": " + describeFailure(failure)
                        + "; trying its command interface.");
                SchedulerHelper.runTask(plugin,
                        () -> dispatchCommandFallback(player, duration, reason));
                return;
            }
            if (result instanceof Optional<?> optional && optional.isEmpty()) {
                plugin.getLogger().warning("LibertyBans did not create a new ban for "
                        + player.getName() + " (the player may already be banned or an event cancelled it).");
                return;
            }
            plugin.getLogger().info("LibertyBans ban enacted for " + player.getName() + ".");
        });
        return true;
    }

    private boolean dispatchCommandFallback(Player player, Duration duration, String reason) {
        if (!installed) {
            return false;
        }
        String durationArgument = formatDuration(duration);
        String cleanReason = reason.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("(?i)§[0-9A-FK-OR]", "")
                .replaceAll("\\s+", " ")
                .trim();
        String command = "libertybans ban " + player.getName() + " "
                + durationArgument + " " + cleanReason;
        boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (accepted) {
            plugin.getLogger().info("Submitted LibertyBans command fallback for "
                    + player.getName() + " (" + durationArgument + ").");
        } else {
            plugin.getLogger().warning("LibertyBans rejected its command fallback for "
                    + player.getName() + ".");
        }
        return accepted;
    }

    private String formatDuration(Duration duration) {
        long milliseconds = Math.max(1L, duration.toMillis());
        long seconds = Math.max(1L, (milliseconds + 999L) / 1000L);
        if (seconds % 86_400L == 0L) return (seconds / 86_400L) + "d";
        if (seconds % 3_600L == 0L) return (seconds / 3_600L) + "h";
        if (seconds % 60L == 0L) return (seconds / 60L) + "m";
        return seconds + "s";
    }

    private String describeFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
