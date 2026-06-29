package com.lovelyspy.integration;

import com.lovelyspy.LovelySpyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

/**
 * Optional LibertyBans API bridge. Reflection keeps LovelySpy loadable when
 * LibertyBans is not installed while still using its native punishment API.
 */
public final class LibertyBansHook {
    private final LovelySpyPlugin plugin;
    private Object api;

    public LibertyBansHook(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("LibertyBans") == null) {
            plugin.getLogger().info("LibertyBans not found; Paper ban storage will be used.");
            return;
        }
        try {
            Class<?> omnibusProvider = Class.forName("space.arim.omnibus.OmnibusProvider");
            Object omnibus = omnibusProvider.getMethod("getOmnibus").invoke(null);
            Object registry = omnibus.getClass().getMethod("getRegistry").invoke(omnibus);
            Class<?> libertyBansClass = Class.forName("space.arim.libertybans.api.LibertyBans");
            Object provider = registry.getClass().getMethod("getProvider", Class.class)
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
        if (api == null) return false;
        try {
            Class<?> punishmentType = Class.forName("space.arim.libertybans.api.PunishmentType");
            Class<?> victimType = Class.forName("space.arim.libertybans.api.Victim");
            Class<?> operatorType = Class.forName("space.arim.libertybans.api.Operator");
            Class<?> playerVictim = Class.forName("space.arim.libertybans.api.PlayerVictim");
            Class<?> consoleOperator = Class.forName("space.arim.libertybans.api.ConsoleOperator");

            Object ban = enumValue(punishmentType, "BAN");
            Object victim = playerVictim.getMethod("of", java.util.UUID.class)
                    .invoke(null, player.getUniqueId());
            Object operator = consoleOperator.getField("INSTANCE").get(null);
            Object drafter = api.getClass().getMethod("getDrafter").invoke(api);
            Object builder = drafter.getClass().getMethod("draftBuilder").invoke(drafter);

            builder = invokeBuilder(builder, "type", punishmentType, ban);
            builder = invokeBuilder(builder, "victim", victimType, victim);
            builder = invokeBuilder(builder, "operator", operatorType, operator);
            builder = invokeBuilder(builder, "reason", String.class, reason);
            builder = invokeBuilder(builder, "duration", Duration.class, duration);
            Object draft = builder.getClass().getMethod("build").invoke(builder);
            draft.getClass().getMethod("enactPunishment").invoke(draft);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("LibertyBans punishment failed: " + exception.getMessage());
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object enumValue(Class<?> type, String value) {
        return Enum.valueOf((Class<? extends Enum>) type, value);
    }

    private Object invokeBuilder(Object builder, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        Method method = builder.getClass().getMethod(name, parameter);
        return method.invoke(builder, value);
    }
}
