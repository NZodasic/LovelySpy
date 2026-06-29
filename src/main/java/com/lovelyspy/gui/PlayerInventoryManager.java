package com.lovelyspy.gui;

import com.lovelyspy.LovelySpyPlugin;
import com.lovelyspy.detection.ClientProfile;
import com.lovelyspy.util.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class PlayerInventoryManager implements Listener {
    private static final int PAGE_SIZE = 45;
    private final LovelySpyPlugin plugin;

    public PlayerInventoryManager(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, int requestedPage) {
        List<RankedPlayer> ranked = Bukkit.getOnlinePlayers().stream()
                .map(this::rank)
                .sorted((first, second) -> {
                    int byPriority = Integer.compare(second.priority(), first.priority());
                    return byPriority != 0 ? byPriority : String.CASE_INSENSITIVE_ORDER.compare(
                            first.player().getName(), second.player().getName());
                })
                .toList();
        int pages = Math.max(1, (ranked.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        PlayerListHolder holder = new PlayerListHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("LovelySpy Review " + (page + 1) + "/" + pages, NamedTextColor.DARK_RED));
        holder.inventory = inventory;

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, ranked.size()); index++) {
            inventory.setItem(index - start, playerItem(ranked.get(index), index + 1));
        }
        if (page > 0) inventory.setItem(45, navigation(Material.ARROW, "Previous page"));
        inventory.setItem(49, navigation(Material.BOOK, "Unknown clients are reviewed first"));
        if (page + 1 < pages) inventory.setItem(53, navigation(Material.ARROW, "Next page"));
        viewer.openInventory(inventory);
    }

    private RankedPlayer rank(Player player) {
        ClientProfile profile = plugin.getVector2().getProfile(player);
        List<Logger.LogEntry> history = plugin.getLoggerService().getHistory(player.getUniqueId().toString());
        boolean unknown = profile.client().equalsIgnoreCase("Unknown")
                || profile.brand().equalsIgnoreCase("unknown");
        int detections = 0;
        int inconclusive = 0;
        for (Logger.LogEntry entry : history) {
            if ("INCONCLUSIVE".equalsIgnoreCase(entry.confidence)) inconclusive++;
            else if (!"NONE".equalsIgnoreCase(entry.confidence)) detections++;
        }
        int priority = (unknown ? 10_000 : 0) + inconclusive * 100 + detections * 10
                + plugin.getOffenseManager().getOffenseCount(player.getUniqueId());
        return new RankedPlayer(player, profile, priority, detections, inconclusive, unknown);
    }

    private ItemStack playerItem(RankedPlayer entry, int rank) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(entry.player());
        NamedTextColor color = entry.unknown() ? NamedTextColor.RED
                : entry.detections() > 0 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        meta.displayName(Component.text("#" + rank + " " + entry.player().getName(), color));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Client: " + entry.profile().client(), NamedTextColor.GRAY));
        lore.add(Component.text("Brand: " + entry.profile().brand(), NamedTextColor.GRAY));
        lore.add(Component.text("Loaders: " + display(entry.profile().loaders()), NamedTextColor.GRAY));
        lore.add(Component.text("Detected mods: " + display(entry.profile().mods()), NamedTextColor.GRAY));
        lore.add(Component.text("Detections: " + entry.detections(), NamedTextColor.YELLOW));
        lore.add(Component.text("Inconclusive scans: " + entry.inconclusive(), NamedTextColor.YELLOW));
        lore.add(Component.text("Review priority: " + entry.priority(), color));
        lore.add(Component.text("Click for full profile", NamedTextColor.AQUA));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String display(java.util.Set<String> values) {
        return values.isEmpty() ? "None" : String.join(", ", values);
    }

    private ItemStack navigation(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof PlayerListHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        int slot = event.getRawSlot();
        if (slot == 45) {
            open(viewer, holder.page - 1);
        } else if (slot == 53) {
            open(viewer, holder.page + 1);
        } else if (slot >= 0 && slot < PAGE_SIZE) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getItemMeta() instanceof SkullMeta skull
                    && skull.getOwningPlayer() != null) {
                Player target = skull.getOwningPlayer().getPlayer();
                if (target != null) {
                    viewer.closeInventory();
                    plugin.getCommands().showPlayerInfo(viewer, target);
                }
            }
        }
    }

    private static final class PlayerListHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        private PlayerListHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record RankedPlayer(Player player, ClientProfile profile, int priority,
                                int detections, int inconclusive, boolean unknown) {}
}
