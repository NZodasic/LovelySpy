package io.papermc.paper.registry.data.dialog;

import net.kyori.adventure.text.Component;
import io.papermc.paper.registry.data.dialog.action.DialogAction;

public interface ActionButton {
    static Builder builder(Component component) {
        return null;
    }

    interface Builder {
        Builder tooltip(Component component);
        Builder action(DialogAction action);
        ActionButton build();
    }
}
