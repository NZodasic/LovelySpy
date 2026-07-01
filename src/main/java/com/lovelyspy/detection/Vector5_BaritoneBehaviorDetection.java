package com.lovelyspy.detection;

import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.config.Config;
import com.lovelyspy.util.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Correlates automation-like behavior with independent GrimAC flags.
 *
 * <p>This does not claim to identify a dormant Baritone installation. It only
 * emits evidence after both LovelySpy and Grim observe suspicious behavior in
 * the same bounded window.</p>
 */
public final class Vector5_BaritoneBehaviorDetection implements Listener {
    private static final int MOVEMENT_WINDOW_SIZE = 40;
    private static final double CENTER_AIM_TOLERANCE_DEGREES = 0.035D;
    private static final Set<String> RELEVANT_GRIM_CHECK_PARTS = Set.of(
            "aim", "rotationbreak", "positionbreak", "fastbreak", "invalidbreak",
            "wrongbreak", "multibreak", "sprint", "timer", "groundspoof",
            "prediction", "nofall", "noslow");

    private final LovelySpyPlugin plugin;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private Listener grimListener;

    public Vector5_BaritoneBehaviorDetection(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public void initializeGrimCorrelation() {
        if (Bukkit.getPluginManager().getPlugin("GrimAC") == null) {
            plugin.getLogger().info("Baritone behavior correlation is waiting for GrimAC.");
            return;
        }
        try {
            Class<?> rawFlagEvent = Class.forName("ac.grim.grimac.api.events.FlagEvent");
            Class<? extends Event> flagEvent = (Class<? extends Event>) rawFlagEvent.asSubclass(Event.class);
            grimListener = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(
                    flagEvent,
                    grimListener,
                    EventPriority.MONITOR,
                    (listener, event) -> handleGrimFlag(event),
                    plugin,
                    true);
            plugin.getLogger().info("Baritone behavior correlation connected to GrimAC FlagEvent.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not connect Baritone correlation to GrimAC: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        recordCenteredTarget(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        recordCenteredTarget(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isEligible(event.getPlayer()) || event.getTo() == null
                || event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isInsideVehicle() || player.isFlying() || player.isGliding() || player.isSwimming()) {
            return;
        }

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontalDistance = Math.hypot(dx, dz);
        if (horizontalDistance < 0.03D || horizontalDistance > 0.9D) {
            return;
        }

        PlayerState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
        if (state.recordMovement(event.getFrom(), event.getTo(), dx, dz)) {
            state.evidence.recordMovementPattern(System.currentTimeMillis());
            evaluate(player, state);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    public void cleanup() {
        states.clear();
        if (grimListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(grimListener);
            grimListener = null;
        }
    }

    private void recordCenteredTarget(Player player, Block block) {
        if (!isEligible(player)) return;

        Location eye = player.getEyeLocation();
        double dx = block.getX() + 0.5D - eye.getX();
        double dy = block.getY() + 0.5D - eye.getY();
        double dz = block.getZ() + 0.5D - eye.getZ();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0E-6D) return;

        double expectedYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double expectedPitch = -Math.toDegrees(Math.atan2(dy, horizontal));
        double yawError = angularDistance(eye.getYaw(), expectedYaw);
        double pitchError = Math.abs(eye.getPitch() - expectedPitch);
        if (yawError > CENTER_AIM_TOLERANCE_DEGREES
                || pitchError > CENTER_AIM_TOLERANCE_DEGREES) {
            return;
        }

        PlayerState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
        String blockKey = block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
        state.evidence.recordCenteredTarget(blockKey, System.currentTimeMillis());
        evaluate(player, state);
    }

    private void handleGrimFlag(Event event) {
        try {
            Class<?> flagType = Class.forName("ac.grim.grimac.api.events.FlagEvent");
            Class<?> identityType = Class.forName("ac.grim.grimac.api.GrimIdentity");
            Class<?> checkType = Class.forName("ac.grim.grimac.api.AbstractCheck");

            Object user = flagType.getMethod("getUser").invoke(event);
            Object check = flagType.getMethod("getCheck").invoke(event);
            UUID uuid = (UUID) identityType.getMethod("getUniqueId").invoke(user);
            String checkName = (String) checkType.getMethod("getCheckName").invoke(check);
            if (!isRelevantGrimCheck(checkName)) return;

            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            long observedAt = System.currentTimeMillis();
            SchedulerHelper.runEntityTask(plugin, player, () -> {
                if (!player.isOnline() || !isEligible(player)) return;
                PlayerState state = states.computeIfAbsent(uuid, ignored -> new PlayerState());
                state.evidence.recordGrimFlag(checkName, observedAt);
                evaluate(player, state);
            });
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Failed to read GrimAC flag for Baritone correlation: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private boolean isRelevantGrimCheck(String checkName) {
        if (checkName == null) return false;
        String normalized = checkName.toLowerCase(Locale.ROOT).replace(" ", "");
        return RELEVANT_GRIM_CHECK_PARTS.stream().anyMatch(normalized::contains);
    }

    private void evaluate(Player player, PlayerState state) {
        Config config = plugin.getLovelyConfig();
        long now = System.currentTimeMillis();
        BaritoneEvidenceWindow.Decision decision = state.evidence.evaluate(
                now,
                config.baritoneEvidenceWindowSeconds * 1000L,
                config.baritoneMinimumCenteredTargets,
                config.baritoneMinimumMovementPatterns,
                config.baritoneMinimumGrimFlags);
        if (!decision.meetsThreshold()) return;

        Config.ModEntry policy = findPolicy("baritone_behavior");
        state.evidence.beginCooldown(now, config.baritoneDetectionCooldownSeconds * 1000L);
        if (policy == null || !policy.enabled) return;

        String evidence = "centered block targets=" + decision.centeredTargets()
                + ", movement-pattern windows=" + decision.movementPatterns()
                + ", Grim flags=" + decision.grimFlags()
                + " " + decision.grimChecks();
        plugin.executeModDetection(
                player,
                policy,
                Map.of("baritone_behavior", evidence),
                "Vector 5 (Baritone Behavioral Correlation)",
                "Automatic");
    }

    private Config.ModEntry findPolicy(String vector) {
        for (Config.ModEntry entry : plugin.getLovelyConfig().modEntries.values()) {
            if (entry.vector != null && entry.vector.equalsIgnoreCase(vector)) {
                return entry;
            }
        }
        return null;
    }

    private boolean isEligible(Player player) {
        return plugin.getLovelyConfig().baritoneBehaviorEnabled
                && !player.hasPermission("lovelyspy.bypass");
    }

    private double angularDistance(double first, double second) {
        double difference = (first - second) % 360.0D;
        if (difference > 180.0D) difference -= 360.0D;
        if (difference < -180.0D) difference += 360.0D;
        return Math.abs(difference);
    }

    private static final class PlayerState {
        private final BaritoneEvidenceWindow evidence = new BaritoneEvidenceWindow();
        private final Deque<MovementSample> movement = new ArrayDeque<>();

        private synchronized boolean recordMovement(Location from, Location to,
                                                    double dx, double dz) {
            double heading = Math.toDegrees(Math.atan2(-dx, dz));
            movement.addLast(new MovementSample(
                    heading,
                    from.getYaw(),
                    to.getYaw(),
                    from.getPitch(),
                    to.getPitch(),
                    to.getX(),
                    to.getZ()));
            if (movement.size() < MOVEMENT_WINDOW_SIZE) {
                return false;
            }

            int alignedHeadings = 0;
            int centerlineSamples = 0;
            int horizontalTurns = 0;
            int pitchChanges = 0;
            Set<Integer> directions = new java.util.HashSet<>();
            for (MovementSample sample : movement) {
                double nearestDirection = Math.rint(sample.heading / 45.0D) * 45.0D;
                if (Math.abs(sample.heading - nearestDirection) <= 0.02D) {
                    alignedHeadings++;
                    directions.add((int) Math.round(nearestDirection));
                }
                double xCenterError = Math.abs(fraction(sample.x) - 0.5D);
                double zCenterError = Math.abs(fraction(sample.z) - 0.5D);
                if (Math.min(xCenterError, zCenterError) <= 0.025D) {
                    centerlineSamples++;
                }
                if (angularDifference(sample.fromYaw, sample.toYaw) > 1.0E-4D
                        && Math.abs(sample.toPitch - sample.fromPitch) <= 1.0E-6D) {
                    horizontalTurns++;
                }
                if (Math.abs(sample.toPitch - sample.fromPitch) > 1.0E-6D) {
                    pitchChanges++;
                }
            }
            movement.clear();
            return alignedHeadings >= 30
                    && centerlineSamples >= 24
                    && horizontalTurns >= 16
                    && pitchChanges <= 1
                    && directions.size() >= 2;
        }

        private static double fraction(double value) {
            return value - Math.floor(value);
        }

        private static double angularDifference(double first, double second) {
            double difference = (first - second) % 360.0D;
            if (difference > 180.0D) difference -= 360.0D;
            if (difference < -180.0D) difference += 360.0D;
            return Math.abs(difference);
        }
    }

    private record MovementSample(double heading,
                                  float fromYaw,
                                  float toYaw,
                                  float fromPitch,
                                  float toPitch,
                                  double x,
                                  double z) {
    }
}
