package com.lovelyspy.config;

import com.lovelyspy.LovelySpyPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import java.util.*;

public final class ConfigDialogManager {
    private final LovelySpyPlugin plugin;

    public ConfigDialogManager(LovelySpyPlugin plugin) {
        this.plugin = plugin;
    }

    private ClickCallback.Options getCallbackOptions() {
        return ClickCallback.Options.builder()
                .uses(100)
                .lifetime(ClickCallback.DEFAULT_LIFETIME)
                .build();
    }

    public void openMainMenu(Player player) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("LovelySpy Admin Menu", NamedTextColor.GOLD))
                .body(List.of(
                    DialogBody.plainMessage(Component.text("Welcome to the LovelySpy anticheat administration panel. Choose an option below to configure features in real-time.", NamedTextColor.GRAY))
                ))
                .build()
            )
            .type(DialogType.multiAction(List.of(
                ActionButton.builder(Component.text("General Settings", NamedTextColor.YELLOW))
                    .tooltip(Component.text("Configure core delays, canary key, and logging"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openGeneralSettings(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Cheat Brands", NamedTextColor.RED))
                    .tooltip(Component.text("Add or remove blocked client brand names"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openCheatBrands(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Cheat Channels", NamedTextColor.LIGHT_PURPLE))
                    .tooltip(Component.text("Configure blocked mod plugin channels"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openCheatChannels(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Legitimate Brands", NamedTextColor.GREEN))
                    .tooltip(Component.text("Whitelisted client brand names"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openLegitimateBrands(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Mod Detections", NamedTextColor.AQUA))
                    .tooltip(Component.text("Configure custom translation keys & actions"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openModDetections(p);
                        }
                    }, getCallbackOptions()))
                    .build()
            )).build())
        );

        player.showDialog(dialog);
    }

    public void openGeneralSettings(Player player) {
        Config config = plugin.getLovelyConfig();
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("General Settings", NamedTextColor.YELLOW))
                .inputs(List.of(
                    DialogInput.numberRange("probe_delay", Component.text("Probe Delay (Ticks)", NamedTextColor.YELLOW), 1f, 200f)
                        .step(1f)
                        .initial((float) config.probeDelayTicks)
                        .width(300)
                        .build(),
                    DialogInput.numberRange("confirm_delay", Component.text("Confirmation Delay (ms)", NamedTextColor.YELLOW), 50f, 5000f)
                        .step(50f)
                        .initial((float) config.confirmationDelayMs)
                        .width(300)
                        .build(),
                    DialogInput.text("canary_key", Component.text("Primary Privacy Control Key", NamedTextColor.YELLOW))
                        .initial(config.canaryKey)
                        .width(300)
                        .build(),
                    DialogInput.text("log_file", Component.text("Log File Path", NamedTextColor.YELLOW))
                        .initial(config.logFile)
                        .width(300)
                        .build()
                ))
                .build()
            )
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Save Settings", NamedTextColor.GREEN))
                    .tooltip(Component.text("Save changes to disk"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            config.probeDelayTicks = view.getFloat("probe_delay").intValue();
                            config.confirmationDelayMs = view.getFloat("confirm_delay").intValue();
                            config.canaryKey = view.getText("canary_key");
                            config.logFile = view.getText("log_file");
                            config.save();
                            p.sendMessage("§a[LovelySpy] General settings saved and reloaded!");
                            openMainMenu(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                    .tooltip(Component.text("Discard changes and go back"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, getCallbackOptions()))
                    .build()
            ))
        );

        player.showDialog(dialog);
    }

    public void openCheatBrands(Player player) {
        Config config = plugin.getLovelyConfig();
        List<String> allBrands = combined(config.knownCheatBrands, config.disabledCheatBrands);
        String currentList = formatToggleList(allBrands, config.knownCheatBrands);
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Configure Cheat Brands", NamedTextColor.RED))
                .body(List.of(
                    DialogBody.plainMessage(Component.text("Currently blocked brands:\n" + currentList, NamedTextColor.GRAY))
                ))
                .inputs(toggleInputs(allBrands, config.knownCheatBrands, "brand_name",
                        "Add new brand name"))
                .build()
            )
            .type(DialogType.multiAction(List.of(
                ActionButton.builder(Component.text("Save Toggles / Add", NamedTextColor.GREEN))
                    .tooltip(Component.text("Save on/off states and optionally add the brand"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            applyToggles(view, allBrands, config.knownCheatBrands, config.disabledCheatBrands);
                            config.save();
                            String brand = view.getText("brand_name");
                            if (brand != null && !brand.trim().isEmpty()) {
                                brand = brand.trim();
                                removeIgnoreCase(config.disabledCheatBrands, brand);
                                if (!config.knownCheatBrands.contains(brand)) {
                                    config.knownCheatBrands.add(brand);
                                    config.save();
                                    p.sendMessage("§a[LovelySpy] Added cheat brand: " + brand);
                                }
                            }
                            openCheatBrands(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Remove Brand", NamedTextColor.RED))
                    .tooltip(Component.text("Remove the brand from block list"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            String brand = view.getText("brand_name");
                            if (brand != null && !brand.trim().isEmpty()) {
                                brand = brand.trim();
                                boolean removed = removeIgnoreCase(config.knownCheatBrands, brand);
                                removed |= removeIgnoreCase(config.disabledCheatBrands, brand);
                                if (removed) {
                                    config.save();
                                    p.sendMessage("§a[LovelySpy] Removed cheat brand: " + brand);
                                }
                            }
                            openCheatBrands(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Back", NamedTextColor.YELLOW))
                    .tooltip(Component.text("Return to main menu"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, getCallbackOptions()))
                    .build()
            )).build())
        );

        player.showDialog(dialog);
    }

    public void openCheatChannels(Player player) {
        Config config = plugin.getLovelyConfig();
        List<String> allChannels = combined(config.knownCheatChannels, config.disabledCheatChannels);
        String currentList = formatToggleList(allChannels, config.knownCheatChannels);
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Configure Cheat Channels", NamedTextColor.LIGHT_PURPLE))
                .body(List.of(
                    DialogBody.plainMessage(Component.text("Currently blocked channels:\n" + currentList
                            + "\nSuggestions: wdl|init, seedcracker, lolimahcker, wdl|control",
                            NamedTextColor.GRAY))
                ))
                .inputs(toggleInputs(allChannels, config.knownCheatChannels, "channel_name",
                        "Add new channel (namespace:path or legacy channel)"))
                .build()
            )
            .type(DialogType.multiAction(List.of(
                ActionButton.builder(Component.text("Save Toggles / Add", NamedTextColor.GREEN))
                    .tooltip(Component.text("Save on/off states and optionally add the channel"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            applyToggles(view, allChannels, config.knownCheatChannels,
                                    config.disabledCheatChannels);
                            config.save();
                            String channel = view.getText("channel_name");
                            if (channel != null && !channel.trim().isEmpty()) {
                                channel = channel.trim();
                                removeIgnoreCase(config.disabledCheatChannels, channel);
                                if (!config.knownCheatChannels.contains(channel)) {
                                    config.knownCheatChannels.add(channel);
                                    config.save();
                                    p.sendMessage("§a[LovelySpy] Added cheat channel: " + channel);
                                }
                            }
                            openCheatChannels(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Remove Channel", NamedTextColor.RED))
                    .tooltip(Component.text("Remove the channel from block list"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            String channel = view.getText("channel_name");
                            if (channel != null && !channel.trim().isEmpty()) {
                                channel = channel.trim();
                                boolean removed = removeIgnoreCase(config.knownCheatChannels, channel);
                                removed |= removeIgnoreCase(config.disabledCheatChannels, channel);
                                if (removed) {
                                    config.save();
                                    p.sendMessage("§a[LovelySpy] Removed cheat channel: " + channel);
                                }
                            }
                            openCheatChannels(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Back", NamedTextColor.YELLOW))
                    .tooltip(Component.text("Return to main menu"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, getCallbackOptions()))
                    .build()
            )).build())
        );

        player.showDialog(dialog);
    }

    public void openLegitimateBrands(Player player) {
        Config config = plugin.getLovelyConfig();
        List<String> allBrands = combined(config.legitimateBrands, config.disabledLegitimateBrands);
        String currentList = formatToggleList(allBrands, config.legitimateBrands);
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Configure Whitelisted Brands", NamedTextColor.GREEN))
                .body(List.of(
                    DialogBody.plainMessage(Component.text("Currently whitelisted brands:\n" + currentList, NamedTextColor.GRAY))
                ))
                .inputs(toggleInputs(allBrands, config.legitimateBrands, "brand_name",
                        "Add new whitelisted brand"))
                .build()
            )
            .type(DialogType.multiAction(List.of(
                ActionButton.builder(Component.text("Save Toggles / Add", NamedTextColor.GREEN))
                    .tooltip(Component.text("Save on/off states and optionally add the brand"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            applyToggles(view, allBrands, config.legitimateBrands,
                                    config.disabledLegitimateBrands);
                            config.save();
                            String brand = view.getText("brand_name");
                            if (brand != null && !brand.trim().isEmpty()) {
                                brand = brand.trim();
                                removeIgnoreCase(config.disabledLegitimateBrands, brand);
                                if (!config.legitimateBrands.contains(brand)) {
                                    config.legitimateBrands.add(brand);
                                    config.save();
                                    p.sendMessage("§a[LovelySpy] Whitelisted brand: " + brand);
                                }
                            }
                            openLegitimateBrands(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Remove Brand", NamedTextColor.RED))
                    .tooltip(Component.text("Remove the brand from whitelist"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            String brand = view.getText("brand_name");
                            if (brand != null && !brand.trim().isEmpty()) {
                                brand = brand.trim();
                                boolean removed = removeIgnoreCase(config.legitimateBrands, brand);
                                removed |= removeIgnoreCase(config.disabledLegitimateBrands, brand);
                                if (removed) {
                                    config.save();
                                    p.sendMessage("§a[LovelySpy] Removed brand from whitelist: " + brand);
                                }
                            }
                            openLegitimateBrands(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Back", NamedTextColor.YELLOW))
                    .tooltip(Component.text("Return to main menu"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, getCallbackOptions()))
                    .build()
            )).build())
        );

        player.showDialog(dialog);
    }

    public void openModDetections(Player player) {
        Config config = plugin.getLovelyConfig();
        String currentList = config.modEntries.values().stream()
                .map(entry -> (entry.enabled ? "ON  " : "OFF ") + entry.name + " [" + entry.action + "]")
                .collect(java.util.stream.Collectors.joining("\n"));
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Configure Mod Detections", NamedTextColor.AQUA))
                .body(List.of(
                    DialogBody.plainMessage(Component.text("Current configured mods:\n" + currentList, NamedTextColor.GRAY))
                ))
                .inputs(modInputs(config))
                .build()
            )
            .type(DialogType.multiAction(List.of(
                ActionButton.builder(Component.text("Save Toggles / Add", NamedTextColor.GREEN))
                    .tooltip(Component.text("Save on/off states and optionally create/update a mod"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            applyModToggles(view, config);
                            config.save();
                            String modName = view.getText("mod_name");
                            if (modName != null && !modName.trim().isEmpty()) {
                                modName = modName.trim().toLowerCase();
                                String keysStr = view.getText("keys");
                                List<String> keys = new ArrayList<>();
                                if (keysStr != null && !keysStr.trim().isEmpty()) {
                                    for (String k : keysStr.split(",")) {
                                        if (!k.trim().isEmpty()) {
                                            keys.add(k.trim());
                                        }
                                    }
                                }
                                String act = view.getText("action");
                                if (act == null || act.trim().isEmpty()) {
                                    act = "FLAG";
                                } else {
                                    act = act.trim().toUpperCase();
                                }
                                String msg = view.getText("message");
                                if (msg == null || msg.trim().isEmpty()) {
                                    msg = "Disallowed mod detected";
                                }
                                String vec = view.getText("vector");
                                if (vec != null && vec.trim().isEmpty()) {
                                    vec = null;
                                }
                                int minMatches = Math.max(1,
                                        view.getFloat("min_matches").intValue());

                                Config.ModEntry mod = new Config.ModEntry(modName, keys, minMatches,
                                        act, msg, vec, true);
                                config.modEntries.put(modName, mod);
                                config.save();
                                p.sendMessage("§a[LovelySpy] Saved/Updated mod entry: " + modName);
                            }
                            openModDetections(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Delete Mod", NamedTextColor.RED))
                    .tooltip(Component.text("Remove this mod configuration"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            String modName = view.getText("mod_name");
                            if (modName != null && !modName.trim().isEmpty()) {
                                modName = modName.trim().toLowerCase();
                                if (config.modEntries.remove(modName) != null) {
                                    config.save();
                                    p.sendMessage("§a[LovelySpy] Deleted mod entry: " + modName);
                                }
                            }
                            openModDetections(p);
                        }
                    }, getCallbackOptions()))
                    .build(),
                ActionButton.builder(Component.text("Back", NamedTextColor.YELLOW))
                    .tooltip(Component.text("Return to main menu"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openMainMenu(p);
                        }
                    }, getCallbackOptions()))
                    .build()
            )).build())
        );

        player.showDialog(dialog);
    }

    private List<String> combined(List<String> enabled, List<String> disabled) {
        LinkedHashSet<String> values = new LinkedHashSet<>(enabled);
        values.addAll(disabled);
        return new ArrayList<>(values);
    }

    private String formatToggleList(List<String> all, List<String> enabled) {
        return all.stream().map(value -> (containsIgnoreCase(enabled, value) ? "ON  " : "OFF ") + value)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<DialogInput> toggleInputs(List<String> all, List<String> enabled,
                                           String addKey, String addLabel) {
        List<DialogInput> inputs = new ArrayList<>();
        for (int index = 0; index < all.size(); index++) {
            String value = all.get(index);
            inputs.add(DialogInput.bool("toggle_" + index, Component.text(value))
                    .initial(containsIgnoreCase(enabled, value)).build());
        }
        inputs.add(DialogInput.text(addKey, Component.text(addLabel, NamedTextColor.YELLOW))
                .width(300).build());
        return inputs;
    }

    private void applyToggles(io.papermc.paper.dialog.DialogResponseView view, List<String> all,
                              List<String> enabled, List<String> disabled) {
        enabled.clear();
        disabled.clear();
        for (int index = 0; index < all.size(); index++) {
            if (Boolean.TRUE.equals(view.getBoolean("toggle_" + index))) enabled.add(all.get(index));
            else disabled.add(all.get(index));
        }
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private boolean removeIgnoreCase(List<String> values, String candidate) {
        for (Iterator<String> iterator = values.iterator(); iterator.hasNext();) {
            if (iterator.next().equalsIgnoreCase(candidate)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private List<DialogInput> modInputs(Config config) {
        List<DialogInput> inputs = new ArrayList<>();
        int index = 0;
        for (Config.ModEntry entry : config.modEntries.values()) {
            inputs.add(DialogInput.bool("mod_toggle_" + index++, Component.text(
                    entry.name + " [" + entry.action + "]")).initial(entry.enabled).build());
        }
        inputs.add(DialogInput.text("mod_name", Component.text("Add/update mod ID", NamedTextColor.YELLOW))
                .width(300).build());
        inputs.add(DialogInput.text("keys", Component.text("Translation keys (comma-separated)",
                NamedTextColor.YELLOW)).width(300).build());
        inputs.add(DialogInput.numberRange("min_matches",
                Component.text("Required matching keys", NamedTextColor.YELLOW), 1f, 3f)
                .initial(1f).step(1f).width(300).build());
        inputs.add(DialogInput.singleOption("action", Component.text("Action", NamedTextColor.YELLOW), List.of(
                SingleOptionDialogInput.OptionEntry.create("FLAG", Component.text("FLAG"), true),
                SingleOptionDialogInput.OptionEntry.create("KICK", Component.text("KICK"), false),
                SingleOptionDialogInput.OptionEntry.create("BAN", Component.text("BAN"), false),
                SingleOptionDialogInput.OptionEntry.create("SHADOW", Component.text("SHADOW"), false)
        )).build());
        inputs.add(DialogInput.text("message", Component.text("Kick/Ban message", NamedTextColor.YELLOW))
                .initial("Disallowed mod detected").width(300).build());
        inputs.add(DialogInput.text("vector", Component.text("Vector (optional)", NamedTextColor.YELLOW))
                .width(300).build());
        return inputs;
    }

    private void applyModToggles(io.papermc.paper.dialog.DialogResponseView view, Config config) {
        int index = 0;
        for (Map.Entry<String, Config.ModEntry> configured :
                new ArrayList<>(config.modEntries.entrySet())) {
            Config.ModEntry entry = configured.getValue();
            boolean enabled = Boolean.TRUE.equals(view.getBoolean("mod_toggle_" + index++));
            config.modEntries.put(configured.getKey(), new Config.ModEntry(entry.name, entry.keys,
                    entry.minMatches, entry.action, entry.message, entry.vector, enabled));
        }
    }
}
