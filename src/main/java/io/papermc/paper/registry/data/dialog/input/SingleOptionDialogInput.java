package io.papermc.paper.registry.data.dialog.input;

import net.kyori.adventure.text.Component;

public interface SingleOptionDialogInput {
    interface OptionEntry {
        static OptionEntry create(String id, Component label, boolean defaultValue) {
            return null;
        }
    }
}
