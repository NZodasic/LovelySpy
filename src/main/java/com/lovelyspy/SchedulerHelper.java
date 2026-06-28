package com.lovelyspy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SchedulerHelper {

    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    public interface LovelyTask {
        void cancel();
    }

    public static LovelyTask runTaskLater(JavaPlugin plugin, Runnable task, long ticks) {
        if (FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), ticks);
            return scheduledTask::cancel;
        } else {
            org.bukkit.scheduler.BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
            return bukkitTask::cancel;
        }
    }

    public static LovelyTask runEntityTaskLater(JavaPlugin plugin, Player player, Runnable task, long ticks) {
        if (FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = 
                player.getScheduler().runDelayed(plugin, t -> task.run(), null, ticks);
            return scheduledTask::cancel;
        } else {
            org.bukkit.scheduler.BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
            return bukkitTask::cancel;
        }
    }

    public static void runTask(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runEntityTask(JavaPlugin plugin, Player player, Runnable task) {
        if (FOLIA) {
            player.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runTaskAsynchronously(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
}
