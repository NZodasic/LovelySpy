package io.papermc.paper.registry.data.dialog.type;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.util.List;

public interface DialogType {
    static MultiActionBuilder multiAction(List<ActionButton> buttons) {
        return null;
    }

    static DialogType confirmation(ActionButton save, ActionButton cancel) {
        return null;
    }

    interface MultiActionBuilder {
        DialogType build();
    }
}
